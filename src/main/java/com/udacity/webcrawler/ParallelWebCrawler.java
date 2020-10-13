package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
    private final Clock clock;
    private final PageParserFactory parserFactory;
    private final Duration timeout;
    private final int popularWordCount;
    private final ForkJoinPool pool;
    private final int maxDepth;
    private final List<Pattern> ignoredUrls;

    private final ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
    private final ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();

    @Inject
    ParallelWebCrawler(
            Clock clock,
            @Timeout Duration timeout,
            @PopularWordCount int popularWordCount,
            @TargetParallelism int threadCount,
            PageParserFactory parserFactory,
            @MaxDepth int maxDepth,
            @IgnoredUrls List<Pattern> ignoredUrls) {
        this.clock = clock;
        this.timeout = timeout;
        this.popularWordCount = popularWordCount;
        this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
        this.parserFactory = parserFactory;
        this.maxDepth = maxDepth;
        this.ignoredUrls = ignoredUrls;
    }

    @Override
    public CrawlResult crawl(List<String> startingUrls) {
        startingUrls.forEach(url -> {
            if (isIgnoredOrIsVisited(url)) return;
            pool.invoke(new CrawlAction(url, maxDepth));
        });
        return new CrawlResult.Builder()
                .setWordCounts(counts.isEmpty() ? counts : WordCounts.sort(counts, popularWordCount))
                .setUrlsVisited(visitedUrls.size())
                .build();
    }

    private boolean isIgnoredOrIsVisited(String url) {
        for (Pattern pattern : ignoredUrls) {
            if (pattern.matcher(url).matches()) {
                return true;
            }
        }
        return visitedUrls.contains(url);
    }

    @Override
    public int getMaxParallelism() {
        return Runtime.getRuntime().availableProcessors();
    }

    public class CrawlAction extends RecursiveAction {
        private final String url;
        private final int maxDepth;

        CrawlAction(String url, int maxDepth) {
            this.url = url;
            this.maxDepth = maxDepth;
        }

        @Override
        protected void compute() {
            Instant deadline = clock.instant().plus(timeout);
            if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
                return;
            }
            visitedUrls.add(url);
            PageParser.Result result = parserFactory.get(url).parse();
            result.getWordCounts().forEach((key, value) -> {
                if (counts.containsKey(key)) {
                    counts.put(key, value + counts.get(key));
                } else {
                    counts.put(key, value);
                }
            });
            result.getLinks().forEach(link -> {
                if (isIgnoredOrIsVisited(link)) return;
                pool.invoke(new CrawlAction(link, maxDepth - 1));
            });
        }
    }
}
