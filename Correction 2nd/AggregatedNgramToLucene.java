package org.languagetool.dev.bigdata;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Indexing the result of {@link CommonCrawlNGramJob} with Lucene.
 * @since 3.2
 */
class AggregatedNgramToLucene extends DuplicatedCodeKs implements AutoCloseable {

  private final Map<Integer, org.languagetool.dev.bigdata.AggregatedNgramToLucene.LuceneIndex> indexes = new HashMap<>();

  private long totalTokenCount = 0;
  private long lineCount = 0;

  AggregatedNgramToLucene(File indexTopDir) throws IOException {
    indexes.put(1, new org.languagetool.dev.bigdata.AggregatedNgramToLucene.LuceneIndex(new File(indexTopDir, "1grams")));
    indexes.put(2, new org.languagetool.dev.bigdata.AggregatedNgramToLucene.LuceneIndex(new File(indexTopDir, "2grams")));
    indexes.put(3, new org.languagetool.dev.bigdata.AggregatedNgramToLucene.LuceneIndex(new File(indexTopDir, "3grams")));
  }

  @Override
  public void close() throws IOException {
    for (org.languagetool.dev.bigdata.AggregatedNgramToLucene.LuceneIndex index : indexes.values()) {
      index.close();
    }
  }

  void indexInputFile(File file) throws IOException {
    System.out.println("=== Indexing " + file + " ===");
    try (Scanner scanner = new Scanner(file)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        indexLine(line);
      }
    }
  }

  private void indexLine(String line) throws IOException {
    if (lineCount++ % 250_000 == 0) {
      System.out.printf(Locale.ENGLISH, "Indexing line %d\n", lineCount);
    }
    String[] lineParts = line.split("\t");
    if (lineParts.length != 2) {
      System.err.println("Not 2 parts but " + lineParts.length + ", ignoring: '" + line + "'");
      return;
    }
    String ngram = lineParts[0];
    String[] ngramParts = ngram.split(" ");
    org.languagetool.dev.bigdata.AggregatedNgramToLucene.LuceneIndex index = indexes.get(ngramParts.length);
    if (index == null) {
      throw new RuntimeException("No ngram data found for: " + Arrays.toString(lineParts));
    }
    long count = Long.parseLong(lineParts[1]);
    if (ngramParts.length == 1) {
      totalTokenCount += count;
    }
    index.indexWriter.addDocument(getDoc(ngram, count));
  }
//  *---------------------------------- old code ----------------------------------*
//  @NotNull
//  @Override
//  public Document getDoc(String ngram, long count) {
//    return super.getDoc(ngram, count);
//  }
//
//  @NotNull
//  @Override
//  public LongField getCountField(long count) {
//    return super.getCountField(count);
//  }
//
//  @Override
//  public void addTotalTokenCountDoc(long totalTokenCount, IndexWriter writer) throws IOException {
//    super.addTotalTokenCountDoc(totalTokenCount, writer);
//  }
//  *------------------------------------------------------------------------------*

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.out.println("Usage: " + org.languagetool.dev.bigdata.AggregatedNgramToLucene.class + " <inputDir>");
      System.out.println(" <inputDir> is a directory with aggregated ngram files from Hadoop, e.g. produced by CommonCrawlNGramJob");
      System.exit(1);
    }
    File inputDir = new File(args[0]);
    File outputDir = new File(inputDir, "index");
    System.out.println("Indexing to " + outputDir);
    try (org.languagetool.dev.bigdata.AggregatedNgramToLucene prg = new org.languagetool.dev.bigdata.AggregatedNgramToLucene(outputDir)) {
      for (File file : inputDir.listFiles()) {
        if (file.isFile()) {
          prg.indexInputFile(file);
        }
      }
      prg.addTotalTokenCountDoc(prg.totalTokenCount, prg.indexes.get(1).indexWriter);
    }
  }

  static class LuceneIndex {

    private final Directory directory;
    private final IndexWriter indexWriter;

    LuceneIndex(File dir) throws IOException {
      Analyzer analyzer = new StandardAnalyzer();
      IndexWriterConfig config = new IndexWriterConfig(analyzer);
      directory = FSDirectory.open(dir.toPath());
      indexWriter = new IndexWriter(directory, config);
    }

    void close() throws IOException {
      indexWriter.close();
      directory.close();
    }

  }
}
