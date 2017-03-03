import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class StemmedCorpusCleaner {

	private static void readFileToStringBuilder(StringBuilder sb, String filePath) throws Exception
	{
		FileReader fr = new FileReader(filePath);
		BufferedReader br = new BufferedReader(fr);
		
		String line = br.readLine();
		while(line != null)
		{
			sb.append(line + " ");
			line = br.readLine();
		}
		
		br.close();
		fr.close();
	}

    public static void cleanCorpus(String cleanedCorpusPath, String rawCorpusFilePath) throws Exception {

        if (Files.exists(Paths.get(cleanedCorpusPath))) {
            deleteFolderWithFiles(cleanedCorpusPath);
            Files.createDirectory(Paths.get(cleanedCorpusPath));
        }
        else {
            Files.createDirectory(Paths.get(cleanedCorpusPath));
        }

        StringBuilder sb = new StringBuilder();
        readFileToStringBuilder(sb, rawCorpusFilePath);

        String filesAsString = sb.toString();

        String[] files = filesAsString.split("# (\\d{0,})");

        int i = 0;
        for(String file: files)
        {
            if(file.equals(""))
            {
                i++;
                continue;
            }

            if(file.startsWith(" "))
                file = file.replaceFirst(" ", "");

            file = file.replaceAll(" pm (\\d{0,}\\s){0,}", " pm");
            file = file.replaceAll(" am (\\d{0,}\\s){0,}", " am");


            Files.createFile(Paths.get(cleanedCorpusPath + File.separatorChar + "CACM-" + String.format("%04d", i) + ".html"));
            Files.write(Paths.get(cleanedCorpusPath + File.separatorChar + "CACM-" + String.format("%04d", i) + ".html"), file.getBytes(), StandardOpenOption.WRITE);

            i++;
        }

    }

    static void deleteFolderWithFiles(String folder) throws IOException {
        Files.walkFileTree(Paths.get(folder), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
        });
        Files.deleteIfExists(Paths.get(folder));
    }

}
