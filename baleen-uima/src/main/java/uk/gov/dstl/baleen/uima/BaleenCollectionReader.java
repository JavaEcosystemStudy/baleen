// Dstl (c) Crown Copyright 2017
// Modified by NCA (c) Crown Copyright 2017
package uk.gov.dstl.baleen.uima;

import java.io.IOException;
import java.io.InputStream;

import org.apache.uima.UimaContext;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.JCasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ExternalResource;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;

import uk.gov.dstl.baleen.core.history.BaleenHistory;
import uk.gov.dstl.baleen.core.metrics.MetricsFactory;
import uk.gov.dstl.baleen.core.pipelines.PipelineBuilder;
import uk.gov.dstl.baleen.core.pipelines.content.ContentExtractor;
import uk.gov.dstl.baleen.uima.utils.UimaUtils;

/**
 * This class provides basic functionality for a collection reader, such as metrics and logging, so
 * that we don't need to put it into every annotator manually. All collection readers in Baleen
 * should inherit from this class and use any utility methods it provides as required to ensure we
 * standardise logging and metrics as much as possible.
 *
 * @baleen.javadoc
 */
public abstract class BaleenCollectionReader extends JCasCollectionReader_ImplBase {
  private UimaMonitor monitor;
  private UimaSupport support;

  /**
   * Baleen History resource
   *
   * @baleen.resource uk.gov.dstl.baleen.core.history.BaleenHistory
   */
  public static final String KEY_HISTORY = PipelineBuilder.BALEEN_HISTORY;

  /**
   * Baleen Content Extractor resource
   *
   * @baleen.resource uk.gov.dstl.baleen.core.pipelines.content.ContentExtractor
   */
  public static final String KEY_CONTENT_EXTRACTOR = PipelineBuilder.CONTENT_EXTRACTOR;

  @ExternalResource(key = KEY_HISTORY, mandatory = false)
  BaleenHistory history;

  @ExternalResource(key = KEY_CONTENT_EXTRACTOR, mandatory = false)
  ContentExtractor contentExtractor;

  @Override
  public final void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context); // This will do initialization of resources,
    // but won't be included in the metrics

    String pipelineName = UimaUtils.getPipelineName(context);
    monitor = new UimaMonitor(pipelineName, this.getClass());
    support =
        new UimaSupport(
            pipelineName,
            this.getClass(),
            history,
            monitor,
            UimaUtils.isMergeDistinctEntities(context));

    monitor.startFunction("initialize");

    doInitialize(context);

    monitor.finishFunction("initialize");
  }

  /**
   * Called when the collection reader is being initialized. Any required resources, for example,
   * should be opened at this point.
   *
   * @param context The UimaContext for the collection reader
   */
  protected abstract void doInitialize(UimaContext context) throws ResourceInitializationException;

  @Override
  public final void getNext(JCas jCas) throws IOException, CollectionException {
    monitor.startFunction("getNext");
    MetricsFactory.getInstance()
        .getPipelineMetrics(monitor.getPipelineName())
        .startDocumentProcess();

    doGetNext(jCas);

    monitor.finishFunction("getNext");
    monitor.persistCounts();
  }

  /**
   * Called when UIMA wants the next document. The passed CAS object should be populated with the
   * document content, and any initial annotations.
   *
   * @param jCas The JCas object to populate
   */
  protected abstract void doGetNext(JCas jCas) throws IOException, CollectionException;

  @Override
  public final void close() throws IOException {
    monitor.startFunction("close");
    contentExtractor.destroy();

    doClose();

    monitor.finishFunction("close");
  }

  /**
   * Called when the collection reader has finished and is closing down. Any open resources, for
   * example, should be closed at this point.
   */
  protected abstract void doClose() throws IOException;

  @Override
  public final Progress[] getProgress() {
    monitor.startFunction("getProgress");

    Progress[] ret = doGetProgress();

    monitor.finishFunction("getProgress");
    return ret;
  }

  @Override
  public void destroy() {
    super.destroy();
    try {
      close();
    } catch (IOException e) {
      getMonitor().warn("Close on destroy", e);
    }
  }

  /**
   * Called when UIMA wants to know how far we've got with processing the current collection of
   * documents. Most collection readers shouldn't need (or won't be able) to implement this as how
   * can we give the progress if we're persistently looking for new data?
   *
   * @return An array of progress objects indicating how far we've currently got.
   */
  public Progress[] doGetProgress() {
    return new Progress[0];
  }

  /**
   * Override of the UIMA hasNext() method with logic to continuously check for new documents until
   * one is found. This prevents the collection reader from exiting (unless asked to), and so
   * creates a persistent collection reader and pipeline.
   */
  @Override
  public final boolean hasNext() throws IOException, CollectionException {
    monitor.startFunctionTrace("hasNext");

    boolean next = doHasNext();

    monitor.finishFunctionTrace("hasNext");
    return next;
  }

  /**
   * Called when UIMA is asking whether there is another document to process. Implementations should
   * return whether there is currently a document available, and not do any waiting for a new
   * document as this is handled by BaleenCollectionReader.
   *
   * @return True if there is another document, false otherwise
   */
  public abstract boolean doHasNext() throws IOException, CollectionException;

  protected UimaMonitor getMonitor() {
    return monitor;
  }

  protected UimaSupport getSupport() {
    return support;
  }

  /**
   * Extract the content from the given inputStream and add it to the supplied jCas.
   *
   * @param inputStream The InputStream of data to process
   * @param source The source URI to set
   * @param jCas The JCas object to add data to
   * @throws IOException
   */
  protected void extractContent(InputStream inputStream, String source, JCas jCas)
      throws IOException {
    contentExtractor.processStream(inputStream, source, jCas);
  }
}
