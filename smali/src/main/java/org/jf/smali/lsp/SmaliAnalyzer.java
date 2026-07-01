/*
 * Copyright 2024, the smali-skills fork.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.jf.smali.lsp;

import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenSource;
import org.antlr.runtime.tree.CommonTree;
import org.jf.smali.InvalidToken;
import org.jf.smali.LexerErrorInterface;
import org.jf.smali.SemanticException;
import org.jf.smali.smaliFlexLexer;
import org.jf.smali.smaliParser;

import javax.annotation.Nonnull;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The pure, side-effect-free analysis core of the smali language server. Given a
 * document's text it produces LSP diagnostics (lexer + parser errors) and a
 * hierarchical document-symbol outline (classes → methods/fields).
 *
 * <p>This class deliberately performs no I/O and holds no state, so it is trivial
 * to unit test independently of the JSON-RPC transport in
 * {@link SmaliLanguageServer}.
 */
public class SmaliAnalyzer {

    /** The default API level used when a client does not specify one. */
    public static final int DEFAULT_API_LEVEL = 15;

    private final int apiLevel;

    public SmaliAnalyzer() {
        this(DEFAULT_API_LEVEL);
    }

    public SmaliAnalyzer(int apiLevel) {
        this.apiLevel = apiLevel;
    }

    /**
     * Lexes and parses {@code source}, returning one diagnostic per lexer or
     * parser error. An empty list means the document assembles cleanly (through
     * the parse phase; the tree walker / dex writer are not run here).
     */
    @Nonnull
    public List<LspModels.Diagnostic> diagnostics(@Nonnull String source) {
        List<LspModels.Diagnostic> diagnostics = new ArrayList<>();

        smaliFlexLexer lexer = new smaliFlexLexer(new StringReader(source), apiLevel);
        CommonTokenStream tokens = new CommonTokenStream((TokenSource) lexer);

        // The parser pulls tokens on demand; lexer errors surface as InvalidToken
        // instances on the ERROR_CHANNEL. Capture them by scanning the filled
        // stream, then run the parser to collect grammar/semantic errors.
        CollectingParser parser = new CollectingParser(tokens, diagnostics);
        parser.setVerboseErrors(false);
        parser.setAllowOdex(false);
        parser.setApiLevel(apiLevel);

        try {
            parser.smali_file();
        } catch (RecognitionException ex) {
            // Reported through displayRecognitionError already; nothing to add.
        } catch (RuntimeException ex) {
            // Defensive: a malformed document should never crash the server.
            diagnostics.add(new LspModels.Diagnostic(
                    LspModels.Range.at(0, 0), LspModels.SEVERITY_ERROR, "smali",
                    "Internal error while parsing: " + ex.getMessage()));
        }

        // Lexer errors: scan the (now fully filled) token stream for InvalidToken.
        for (Object o : tokens.getTokens()) {
            Token token = (Token) o;
            if (token instanceof InvalidToken) {
                InvalidToken invalid = (InvalidToken) token;
                diagnostics.add(new LspModels.Diagnostic(
                        rangeForToken(token),
                        LspModels.SEVERITY_ERROR,
                        "smali",
                        invalid.getMessage() + " (near '" + invalid.getText() + "')"));
            }
        }

        return diagnostics;
    }

    /**
     * Parses {@code source} and returns the class/method/field outline. Parse
     * errors are tolerated: whatever partial tree ANTLR recovers is walked, so an
     * outline is still produced for the valid portion of a broken document.
     */
    @Nonnull
    public List<LspModels.DocumentSymbol> documentSymbols(@Nonnull String source) {
        List<LspModels.DocumentSymbol> symbols = new ArrayList<>();

        smaliFlexLexer lexer = new smaliFlexLexer(new StringReader(source), apiLevel);
        CommonTokenStream tokens = new CommonTokenStream((TokenSource) lexer);

        smaliParser parser = new smaliParser(tokens);
        parser.setVerboseErrors(false);
        parser.setAllowOdex(false);
        parser.setApiLevel(apiLevel);

        CommonTree tree;
        try {
            smaliParser.smali_file_return result = parser.smali_file();
            tree = result.getTree();
        } catch (RecognitionException | RuntimeException ex) {
            return symbols;
        }
        if (tree == null) {
            return symbols;
        }

        collectSymbols(tree, symbols);
        return symbols;
    }

    // -- symbol walking ------------------------------------------------------

    private void collectSymbols(CommonTree node, List<LspModels.DocumentSymbol> out) {
        if (node.getType() == smaliParser.I_CLASS_DEF) {
            out.add(buildClassSymbol(node));
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSymbols((CommonTree) node.getChild(i), out);
        }
    }

    private LspModels.DocumentSymbol buildClassSymbol(CommonTree classDef) {
        CommonTree descriptor = firstChildOfType(classDef, smaliParser.CLASS_DESCRIPTOR);
        String name = descriptor != null ? descriptor.getText() : "<class>";
        LspModels.Range range = descriptor != null ? rangeForNode(descriptor) : rangeForNode(classDef);

        LspModels.DocumentSymbol classSymbol =
                new LspModels.DocumentSymbol(name, null, LspModels.SYMBOL_CLASS, range);
        classSymbol.children = new ArrayList<>();

        CommonTree methods = firstChildOfType(classDef, smaliParser.I_METHODS);
        if (methods != null) {
            for (int i = 0; i < methods.getChildCount(); i++) {
                CommonTree method = (CommonTree) methods.getChild(i);
                if (method.getType() == smaliParser.I_METHOD) {
                    classSymbol.children.add(buildMethodSymbol(method));
                }
            }
        }

        CommonTree fields = firstChildOfType(classDef, smaliParser.I_FIELDS);
        if (fields != null) {
            for (int i = 0; i < fields.getChildCount(); i++) {
                CommonTree field = (CommonTree) fields.getChild(i);
                if (field.getType() == smaliParser.I_FIELD) {
                    classSymbol.children.add(buildFieldSymbol(field));
                }
            }
        }

        return classSymbol;
    }

    private LspModels.DocumentSymbol buildMethodSymbol(CommonTree method) {
        // ^(I_METHOD member_name method_prototype access_or_restriction_list ...)
        String memberName = method.getChildCount() > 0 ? leafText((CommonTree) method.getChild(0)) : "<method>";
        CommonTree prototype = firstChildOfType(method, smaliParser.I_METHOD_PROTOTYPE);
        String signature = memberName + prototypeText(prototype);
        return new LspModels.DocumentSymbol(
                signature, "method", LspModels.SYMBOL_METHOD, rangeForNode(method));
    }

    private LspModels.DocumentSymbol buildFieldSymbol(CommonTree field) {
        // ^(I_FIELD member_name access ^(I_FIELD_TYPE type) ...)
        String memberName = field.getChildCount() > 0 ? leafText((CommonTree) field.getChild(0)) : "<field>";
        CommonTree fieldType = firstChildOfType(field, smaliParser.I_FIELD_TYPE);
        String type = fieldType != null ? leafText(fieldType) : "";
        String signature = type.isEmpty() ? memberName : memberName + ":" + type;
        return new LspModels.DocumentSymbol(
                signature, "field", LspModels.SYMBOL_FIELD, rangeForNode(field));
    }

    /** Reconstructs {@code (params)returnType} from an I_METHOD_PROTOTYPE subtree. */
    private String prototypeText(CommonTree prototype) {
        if (prototype == null) {
            return "()V";
        }
        StringBuilder params = new StringBuilder();
        String returnType = "V";
        for (int i = 0; i < prototype.getChildCount(); i++) {
            CommonTree child = (CommonTree) prototype.getChild(i);
            if (child.getType() == smaliParser.I_METHOD_RETURN_TYPE) {
                returnType = leafText(child);
            } else {
                params.append(leafText(child));
            }
        }
        return "(" + params + ")" + returnType;
    }

    // -- tree helpers --------------------------------------------------------

    private static CommonTree firstChildOfType(CommonTree node, int type) {
        for (int i = 0; i < node.getChildCount(); i++) {
            CommonTree child = (CommonTree) node.getChild(i);
            if (child.getType() == type) {
                return child;
            }
        }
        return null;
    }

    /** Concatenates the text of all leaf tokens under {@code node}, in order. */
    private static String leafText(CommonTree node) {
        if (node.getChildCount() == 0) {
            String text = node.getText();
            return text != null ? text : "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.getChildCount(); i++) {
            sb.append(leafText((CommonTree) node.getChild(i)));
        }
        return sb.toString();
    }

    // -- position mapping ----------------------------------------------------

    private static LspModels.Range rangeForNode(CommonTree node) {
        int line = Math.max(0, node.getLine() - 1); // ANTLR line is 1-based
        int col = Math.max(0, node.getCharPositionInLine());
        String text = node.getText();
        int length = text != null ? text.length() : 0;
        return new LspModels.Range(
                new LspModels.Position(line, col),
                new LspModels.Position(line, col + length));
    }

    private static LspModels.Range rangeForToken(Token token) {
        int line = Math.max(0, token.getLine() - 1);
        int col = Math.max(0, token.getCharPositionInLine());
        String text = token.getText();
        int length = text != null ? text.length() : 1;
        return new LspModels.Range(
                new LspModels.Position(line, col),
                new LspModels.Position(line, col + length));
    }

    // -- error-collecting parser --------------------------------------------

    /**
     * A {@link smaliParser} subclass that redirects recognition errors into a
     * diagnostics list instead of printing them to stderr.
     */
    private static final class CollectingParser extends smaliParser {
        private final List<LspModels.Diagnostic> diagnostics;

        CollectingParser(CommonTokenStream tokens, List<LspModels.Diagnostic> diagnostics) {
            super(tokens);
            this.diagnostics = diagnostics;
        }

        @Override
        public void displayRecognitionError(String[] tokenNames, RecognitionException e) {
            String message;
            if (e instanceof SemanticException) {
                message = e.getMessage();
            } else {
                message = getErrorMessage(e, tokenNames);
            }
            int line = Math.max(0, e.line - 1);
            int col = Math.max(0, e.charPositionInLine);
            diagnostics.add(new LspModels.Diagnostic(
                    LspModels.Range.at(line, col),
                    LspModels.SEVERITY_ERROR,
                    "smali",
                    message));
        }
    }
}
