/**
 *  Copyright 2014-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.fusesource.distrodiff;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by chirino on 7/21/14.
 */
public class Main {

    static private void error(String message) {
        throw new FatalError(message);
    }

    static class FatalError extends RuntimeException {
        FatalError(String message) {
            super(message);
        }
    }

    static private void mustBeReadable(Path file) {
        if (!Files.exists(file)) {
            error("File does not exist: " + file);
        }
        if (!Files.isReadable(file)) {
            error("Don't have enough permissions to read: " + file);
        }
    }

    static private String shiftArgument(LinkedList<String> arguments, String s) {
        if( arguments.isEmpty() ) {
            error("Expected an argument to the "+s+" command line option");
        }
        return arguments.removeFirst();
    }

    static protected String binaryCompare(Path file1, Path file2) throws IOException {
        try (BufferedInputStream is1 = new BufferedInputStream(Files.newInputStream(file1));
             BufferedInputStream is2 = new BufferedInputStream(Files.newInputStream(file2))) {
            long position = 0;
            while (true) {
                int c1 = is1.read();
                int c2 = is2.read();

                if (c1 < 0 && c2 < 0) {
                    break;
                }
                if (c1 < 0 || c2 < 0) {
                    return "Different size.";
                }
                if (c1 != c2) {
                    return "A binary difference found at byte offset: " + position;
                }
                position++;
            }

//        } catch (IOException e) {
//            error(String.format("Could not compare '%s' with '%s': %s", file1, file2, e.getMessage()));
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        try {
            new Diff().exec(args);
            System.exit(0);
        } catch (FatalError e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    static class Report {

        private String path1;
        private String path2;
        public LinkedList<Report> childReports;
        public String note;
        public boolean file;

        public Report(String path1, String path2, String note) {
            this(path1, path2, note, new LinkedList<>());
        }

        public Report(String path1, String path2, String note, LinkedList<Report> childReports) {
            this.path1 = path1;
            this.path2 = path2;
            this.note = note;
            this.childReports = childReports;
        }

        public void dump(PrintStream out, boolean verbose, String sp1, String sp2, String indent) {

            String fp1 = path1;
            fp1 = fp1.replaceFirst("^"+Pattern.quote(sp1), "");

            String fp2 = path2;
            fp2 = fp2.replaceFirst("^"+Pattern.quote(sp2), "");

            if( note!=null && ( verbose || ( file  && !childReports.isEmpty())) ) {
                if( !fp2.equals("<missing>") ) {
                    out.println(String.format("%s%s: %s", indent, fp2, note));
                } else {
                    out.println(String.format("%s%s: %s\n", indent, fp1, note));
                }
            }

            for (Report childReport : childReports) {
                if( file ) {
                    childReport.dump(out, verbose, path1+"!", path2+"!", indent+"  ");
                } else {
                    childReport.dump(out, verbose, sp1, sp2, indent);
                }
            }
        }
    }

    static class Diff {

        HashSet<Pattern> ignoredPaths = new HashSet<>();
        boolean verbose = false;

        public void exec(String[] args) throws IOException {

            LinkedList<String> arguments = new LinkedList(Arrays.asList(args));

            ignoredPaths.add(Pattern.compile(Pattern.quote("/META-INF/NOTICE")));
            ignoredPaths.add(Pattern.compile(Pattern.quote("/META-INF/MANIFEST.MF")));
            ignoredPaths.add(Pattern.compile(Pattern.quote("/META-INF/DEPENDENCIES")));
            ignoredPaths.add(Pattern.compile(Pattern.quote("/META-INF/maven/")+".*"));

            Path file1 = null;
            Path file2 = null;

            while(!arguments.isEmpty()) {
                String arg = arguments.removeFirst();
                if( arg.equals("-i") ) {
                    ignoredPaths.add(Pattern.compile(Pattern.quote(shiftArgument(arguments, "-i"))));
                } else if( arg.equals("-v") ) {
                    verbose = true;
                } else {
                    arguments.addFirst(arg);
                    break;
                }
            }

            if (arguments.size() < 2) {
                error("Source and Target files not specified.");
            }
            file1 = FileSystems.getDefault().getPath(arguments.removeFirst());
            file2 = FileSystems.getDefault().getPath(arguments.removeFirst());

            mustBeReadable(file1);
            mustBeReadable(file2);

            Report diff = compare("", "", file1, file2);
            if( diff!=null ) {
                diff.file = false;
                diff.dump(System.out, verbose, "", "", "");
            } else {
                System.out.println("No difference found.");
            }
        }


        protected Report compare(String prefix1, String prefix2, Path file1, Path file2) throws IOException {

            if (Files.isDirectory(file1)) {
                if (Files.isDirectory(file2)) {
                    return directoryCompare(prefix1, prefix2, file1, file2);
                } else {
                    return new Report(prefix1+file1, prefix2+file2, "directory/file type difference");
                }
            } else {
                if (Files.isDirectory(file2)) {
                    return new Report(prefix1+file1, prefix2+file2, "file/directory type difference");
                } else {
                    return fileCompare(prefix1, prefix2, file1, file2);
                }
            }
        }

        protected Report fileCompare(String prefix1, String prefix2, Path file1, Path file2) throws IOException {

            for (Pattern pattern : ignoredPaths) {
                if( pattern.matcher(file1.toString()).matches() && pattern.matcher(file2.toString()).matches() ) {
                    return null;
                }
            }

            // If it's binary the same.. no need to dig too deep into the file...
            String binaryDiff = binaryCompare(file1, file2);
            if (binaryDiff == null) {
                return null;
            }

            // Then it's binary identical, but it might still be logically the same if it's
            // an archive..
            String name = file1.getFileName().toString();
            if( name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".war") ) {
                debug("comparing zip contents       '%s%s'\n" +
                      "                  with       '%s%s'", prefix1, file1, prefix2, file2);
                if (file1.getFileSystem() != FileSystems.getDefault()) {
                    // We need to copy it to the default file system first.
                    Path t1 = Files.createTempFile("t", "-"+file1.getFileName().toString());
                    Path t2 = Files.createTempFile("t", "-"+file2.getFileName().toString());
                    try {
                        Files.copy(file1, t1, StandardCopyOption.REPLACE_EXISTING);
                        Files.copy(file2, t2, StandardCopyOption.REPLACE_EXISTING);
                        return zipCompare(prefix1+file1, prefix2+file2, t1, t2);
                    } finally {
                        Files.delete(t1);
                        Files.delete(t2);
                    }
                } else {
                    return zipCompare(prefix1 + file1, prefix2 + file2, file1, file2);
                }
            }

            Report report = new Report(prefix1+file1, prefix2+file2, binaryDiff);
            report.file = true;
            return report;
        }

        private Report zipCompare(String path1, String path2, Path file1, Path file2) throws IOException {
            try (
                FileSystem zipfs1 = FileSystems.newFileSystem(file1, null);
                FileSystem zipfs2 = FileSystems.newFileSystem(file2, null)
            ) {
                Report report = directoryCompare(path1+"!", path2+"!", file1, file2, zipfs1.getRootDirectories(), zipfs2.getRootDirectories());
                if( report!=null ) {
                    report.path1 = path1;
                    report.path2 = path2;
                    report.file = true;
                    if( report.note == null ) {
                        report.note = "different";
                    }
                }
                return report;
            }
        }

        private void debug(String msg, Object...args) {
//            System.out.println(String.format(msg, args));
        }

        protected Report directoryCompare(String prefix1, String prefix2, Path dir1, Path dir2) throws IOException {
            try (DirectoryStream<Path> stream1 = Files.newDirectoryStream(dir1);
                 DirectoryStream<Path> stream2 = Files.newDirectoryStream(dir2);
            ) {
                debug("comparing directory contents '%s%s'\n" +
                      "                        with '%s%s'", prefix1, dir1, prefix2, dir2);
                return directoryCompare(prefix1, prefix2, dir1, dir2, stream1, stream2);
            }
        }

        protected Report directoryCompare(String prefix1, String prefix2, Path dir1, Path dir2, Iterable<Path> stream1, Iterable<Path> stream2) throws IOException {
            HashMap<String, Path> entries1 = new HashMap<>();
            for (Path entry : stream1) {
                Path key = entry;
                if( key.getFileName()!=null ) {
                    key = entry.getFileName();
                }
                entries1.put(key.toString(), entry);
            }

            HashMap<String, Path> entries2 = new HashMap<>();
            for (Path entry : stream2) {
                Path key = entry;
                if( key.getFileName()!=null ) {
                    key = entry.getFileName();
                }
                entries2.put(key.toString(), entry);
            }

            HashSet<String> added = new HashSet<>(entries2.keySet());
            added.removeAll(entries1.keySet());

            HashSet<String> removed = new HashSet<>(entries1.keySet());
            removed.removeAll(entries2.keySet());

            HashSet<String> matching = new HashSet<>(entries1.keySet());
            matching.retainAll(entries2.keySet());


            // Lets correlate renamed entries..
            HashMap<String, String> renames = correlateRenamed(removed, added, entries1, entries2);
            LinkedList<Report> childs = new LinkedList<>();

            if( !added.isEmpty() ) {
                for (String s : added) {
                    childs.add(new Report("<missing>", prefix2 + entries2.get(s), "added"));
                }
            }

            if( !removed.isEmpty() ) {
                for (String s : removed) {
                    childs.add(new Report(prefix1 + entries1.get(s), "<missing>", "removed"));
                }
            }

            if( !matching.isEmpty() ) {
                for (String s : matching) {
                    Report childReport = compare(prefix1, prefix2, entries1.get(s), entries2.get(s));
                    if( childReport!=null ) {
                        childs.add(childReport);
                    }
                }
            }

            if( !renames.isEmpty() ) {
                for (Map.Entry<String, String> entry : renames.entrySet()) {
                    Report childReport = compare(prefix1, prefix2, entries1.get(entry.getKey()), entries2.get(entry.getValue()));
                    if( childReport!=null ) {
                        childs.add(childReport);
                    }
                }
            }

            if( childs.isEmpty() ) {
                return null;
            }

            return new Report(prefix1+dir1, prefix2+dir2, null, childs);
        }

        private HashMap<String, String> correlateRenamed(HashSet<String> removed, HashSet<String> added, HashMap<String, Path> entries1, HashMap<String, Path> entries2) {
            HashMap<String, String> matches = new HashMap<>();
            for (String s1 : new HashSet<>(removed)) {
                String bestMatch = null;
                int bestScore=0;
                for (String s2 : new HashSet<>(added)) {
                    if( Files.isDirectory(entries1.get(s1)) == Files.isDirectory(entries2.get(s2)) ) {
                        int score = Files.isDirectory(entries1.get(s1)) ? directoryMatchScore(s1, s2) : fileMatchScore(s1, s2);
                        if( score > bestScore ) {
                            bestMatch = s2;
                        }
                    }
                }
                if( bestMatch!=null ) {
                    matches.put(s1, bestMatch);
                    removed.remove(s1);
                    added.remove(bestMatch);
                }
            }
            return matches;
        }

        private int directoryMatchScore(String s1, String s2) {
            boolean digit_found = false;
            int prefixScore=0;
            while( prefixScore < s1.length() && prefixScore < s2.length() ) {
                char c1 = s1.charAt(prefixScore);
                char c2 = s2.charAt(prefixScore);

                if( Character.isDigit(c1) && Character.isDigit(c2) ) {
                    digit_found = true;
                }
                if( c1 == c2) {
                    prefixScore++;
                } else {
                    break;
                }
            }

            return digit_found ? prefixScore : 0;
        }

        private int fileMatchScore(String s1, String s2) {
            int prefixScore = directoryMatchScore(s1, s2);

            boolean dot_found = false;
            int suffixScore=0;
            while( suffixScore <= s1.length() && suffixScore <= s2.length() ) {
                char c1 = s1.charAt(s1.length()-suffixScore-1);
                char c2 = s2.charAt(s2.length()-suffixScore-1);
                if( c1 == c2) {
                    if( c1=='.' ) {
                        dot_found = true;
                    }
                    suffixScore++;
                } else {
                    break;
                }
            }

            return prefixScore!=0 && dot_found ? prefixScore+suffixScore : 0;
        }


    }


}
