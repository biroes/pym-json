package com.biroes.pym.json.cli;

import com.biroes.pym.json.Json;
import com.biroes.pym.json.JsonConfig;
import com.biroes.pym.json.JsonException;
import com.biroes.pym.json.model.JsonValue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command line pretty-printer built on top of pym-json.
 * <p>
 * Reads JSON from a file argument or, when none is given (or {@code -} is
 * passed), from standard input; parses it into a {@link JsonValue} tree and
 * writes it back out formatted according to the supplied options.
 *
 * <pre>
 *   ppjson [options] [file]
 *
 *   options:
 *     -i, --indent &lt;N&gt;   indent size in spaces (default 2)
 *     -t, --tab          use a single tab per level (overrides --indent)
 *     -c, --compact      compact, single-line output
 *     -n, --no-nulls     omit object members whose value is null
 *     -h, --help         show this help and exit
 * </pre>
 */
public final class PrettyPrintCli {

    private static final int EXIT_OK = 0;
    private static final int EXIT_USAGE = 2;
    private static final int EXIT_ERROR = 1;

    private PrettyPrintCli() {
    }

    public static void main(String[] args) {
        Options opts = parse(args);
        if (opts == null) {
            System.exit(EXIT_USAGE);
            return;
        }
        if (opts.help) {
            printUsage(System.out);
            System.exit(EXIT_OK);
            return;
        }

        int exit = run(opts);
        System.exit(exit);
    }

    private static int run(Options opts) {
        JsonConfig config = JsonConfig.builder()
                .prettyPrint(!opts.compact)
                .indent(opts.indent)
                .serializeNulls(!opts.noNulls)
                .build();
        Json json = Json.create(config);

        try (InputStream in = openInput(opts);
             Writer out = new OutputStreamWriter(System.out, StandardCharsets.UTF_8)) {

            JsonValue tree;
            try (InputStream buffered = new BufferedInputStream(in)) {
                tree = json.readTree(buffered);
            }
            json.write(tree, out);
            out.write('\n');
            return EXIT_OK;
        } catch (IOException e) {
            System.err.println("ppjson: " + e.getMessage());
            return EXIT_ERROR;
        } catch (JsonException e) {
            System.err.println("ppjson: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private static InputStream openInput(Options opts) throws IOException {
        if (opts.file == null || "-".equals(opts.file)) {
            return System.in;
        }
        Path path = Path.of(opts.file);
        if (!Files.isReadable(path)) {
            throw new IOException("cannot read file: " + path);
        }
        return Files.newInputStream(path);
    }

    // ------------------------------------------------------------------
    // Argument parsing
    // ------------------------------------------------------------------

    private static Options parse(String[] args) {
        Options opts = new Options();
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h", "--help" -> opts.help = true;
                case "-" -> positional.add(a);
                case "-t", "--tab" -> {
                    opts.indent = "\t";
                    opts.indentSet = true;
                }
                case "-c", "--compact" -> opts.compact = true;
                case "-n", "--no-nulls" -> opts.noNulls = true;
                case "-i", "--indent" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("ppjson: " + a + " requires an argument");
                        return null;
                    }
                    String raw = args[++i];
                    int size;
                    try {
                        size = Integer.parseInt(raw);
                    } catch (NumberFormatException e) {
                        System.err.println("ppjson: --indent expects a number, got: " + raw);
                        return null;
                    }
                    if (size < 0) {
                        System.err.println("ppjson: --indent must be non-negative");
                        return null;
                    }
                    if (!opts.indentSet) {
                        opts.indent = " ".repeat(size);
                    }
                }
                default -> {
                    if (a.startsWith("--indent=")) {
                        String raw = a.substring("--indent=".length());
                        int size;
                        try {
                            size = Integer.parseInt(raw);
                        } catch (NumberFormatException e) {
                            System.err.println("ppjson: --indent expects a number, got: " + raw);
                            return null;
                        }
                        if (size < 0) {
                            System.err.println("ppjson: --indent must be non-negative");
                            return null;
                        }
                        if (!opts.indentSet) {
                            opts.indent = " ".repeat(size);
                        }
                    } else if (a.startsWith("-")) {
                        System.err.println("ppjson: unknown option: " + a);
                        return null;
                    } else {
                        positional.add(a);
                    }
                }
            }
        }

        if (positional.size() > 1) {
            System.err.println("ppjson: too many arguments (expected at most one file)");
            return null;
        }
        if (!positional.isEmpty()) {
            opts.file = positional.get(0);
        }
        return opts;
    }

    private static void printUsage(java.io.PrintStream out) {
        out.println("""
                Usage: ppjson [options] [file]

                  Reads JSON from <file> or, when omitted / '-', from standard
                  input and writes it back pretty-printed to standard output.

                Options:
                  -i, --indent <N>   indent size in spaces (default 2)
                  -t, --tab          use a tab per level (overrides --indent)
                  -c, --compact      compact, single-line output
                  -n, --no-nulls     omit object members whose value is null
                  -h, --help         show this help and exit
                """);
    }

    private static final class Options {
        String file = null;
        String indent = "  ";
        boolean indentSet = false;
        boolean compact = false;
        boolean noNulls = false;
        boolean help = false;
    }
}
