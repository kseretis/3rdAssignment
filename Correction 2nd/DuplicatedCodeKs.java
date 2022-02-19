package org.languagetool.dev.bigdata;

import org.apache.lucene.document.*;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

// Smell Dedector, New class
public class DuplicatedCodeKs {

  @NotNull
  public Document getDoc(String ngram, long count) {
    Document doc = new Document();
    doc.add(new Field("ngram", ngram, StringField.TYPE_NOT_STORED));  // use StringField.TYPE_STORED for easier debugging with e.g. Luke
    doc.add(getCountField(count));
    return doc;
  }

  @NotNull
  public LongField getCountField(long count) {
    FieldType fieldType = new FieldType();
    fieldType.setStored(true);
    fieldType.setOmitNorms(true);
    fieldType.setNumericType(FieldType.NumericType.LONG);
    fieldType.setDocValuesType(DocValuesType.NUMERIC);
    return new LongField("count", count, fieldType);
  }

  public void addTotalTokenCountDoc(long totalTokenCount, IndexWriter writer) throws IOException {
    FieldType fieldType = new FieldType();
    fieldType.setIndexOptions(IndexOptions.DOCS);
    fieldType.setStored(true);
    fieldType.setOmitNorms(true);
    Field countField = new Field("totalTokenCount", String.valueOf(totalTokenCount), fieldType);
    Document doc = new Document();
    doc.add(countField);
    writer.addDocument(doc);
  }

}
