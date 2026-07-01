/*
 * Copyright 2026, Google Inc.
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

package org.jf.baksmali;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.jf.baksmali.mcp.McpServer;
import org.jf.util.jcommander.Command;
import org.jf.util.jcommander.ExtendedParameters;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

/**
 * {@code baksmali mcp} — starts a Model Context Protocol server over stdio, exposing baksmali's
 * read-only dex query capabilities (list / disassemble / search / xref) as MCP tools so an AI agent
 * host can drive them directly.
 *
 * <p>Intended to be launched by an MCP host (Claude Desktop, IDE agents, …), not run interactively.
 * The dex/apk to inspect is passed per tool-call as the {@code input} argument, so no input file is
 * given on the command line.
 */
@Parameters(
        commandDescription = "Starts a Model Context Protocol (MCP) server over stdio, exposing " +
                "read-only dex queries (list/disassemble/search/xref) as tools for AI agents.")
@ExtendedParameters(
        commandName = "mcp")
public class McpCommand extends Command {

    @Parameter(names = {"-h", "-?", "--help"}, help = true,
            description = "Show usage information for this command.")
    private boolean help;

    @Parameter(names = {"-a", "--api"},
            description = "The API level to use when loading and disassembling dex files.")
    private int apiLevel = 15;

    public McpCommand(@Nonnull List<JCommander> commandAncestors) {
        super(commandAncestors);
    }

    @Override public void run() {
        if (help) {
            usage();
            return;
        }
        try {
            new McpServer(apiLevel).run(System.in, System.out);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
