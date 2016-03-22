package com.ingestion;

import com.metamx.common.logger.Logger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.metamx.common.Granularity;
import com.metamx.tranquility.beam.ClusteredBeamTuning;
import com.metamx.tranquility.druid.DruidBeams;
import com.metamx.tranquility.druid.DruidDimensions;
import com.metamx.tranquility.druid.DruidLocation;
import com.metamx.tranquility.druid.DruidRollup;
import com.metamx.tranquility.tranquilizer.MessageDroppedException;
import com.metamx.tranquility.tranquilizer.Tranquilizer;
import com.metamx.tranquility.typeclass.Timestamper;
import com.twitter.util.FutureEventListener;
import io.druid.data.input.impl.TimestampSpec;
import io.druid.granularity.QueryGranularity;
import io.druid.query.aggregation.*;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.joda.time.DateTime;
import org.joda.time.Period;
import scala.runtime.BoxedUnit;

import java.util.*;


public class Ingestion {

    private static final Logger log = new Logger(Ingestion.class);

    public static void main(String[] args) {
        final String zkConn = "172.16.8.211:2181";
        final String indexService = "druid:prod:overlord"; // Your overlord's druid.service
        final String discoveryPath = "/druid/prod/discovery"; // Your overlord's druid.discovery.curator.path
        final String dataSource = "wikipedia";

        final List<String> dimensions = ImmutableList.of(
                "page",
                "language",
                "user",
                "unpatrolled",
                "newPage",
                "robot",
                "anonymous",
                "namespace",
                "continent",
                "country",
                "region",
                "city");
        final List<AggregatorFactory> aggregators = ImmutableList.of(
                new CountAggregatorFactory("count"),
                new DoubleSumAggregatorFactory("editCastTime", "editCastTime")
        );
        // Tranquility needs to be able to extract timestamps from your object type (in this case, Map<String, Object>).
        final Timestamper<Map<String, Object>> timestamper = new Timestamper<Map<String, Object>>() {
            public DateTime timestamp(Map<String, Object> theMap) {
                return new DateTime(theMap.get("timestamp"));
            }
        };
        // Tranquility uses ZooKeeper (through Curator) for coordination.
        final CuratorFramework curator = CuratorFrameworkFactory
                .builder()
                .connectString(zkConn)
                .retryPolicy(new ExponentialBackoffRetry(1000, 20, 30000))
                .build();
        curator.start();

        // The JSON serialization of your object must have a timestamp field in a format that Druid understands. By default,
        // Druid expects the field to be called "timestamp" and to be an ISO8601 timestamp.
        final TimestampSpec timestampSpec = new TimestampSpec("timestamp", "auto", null);

        // Tranquility needs to be able to serialize your object type to JSON for transmission to Druid. By default this is
        // done with Jackson. If you want to provide an alternate serializer, you can provide your own via ```.objectWriter(...)```.
        // In this case, we won't provide one, so we're just using Jackson.
        final Tranquilizer<Map<String, Object>> sender = DruidBeams
                .builder(timestamper)
                .curator(curator)
                .discoveryPath(discoveryPath)
                .location(DruidLocation.create(indexService, dataSource))
                .timestampSpec(timestampSpec)
                .rollup(DruidRollup.create(DruidDimensions.specific(dimensions), aggregators, QueryGranularity.MINUTE))
                .tuning(
                        ClusteredBeamTuning
                                .builder()
                                .segmentGranularity(Granularity.HOUR)
                                .windowPeriod(new Period("PT30M"))
                                .partitions(1)
                                .replicants(1)
                                .build()
                )
                .buildTranquilizer();

        sender.start();

        try {
            // Send 10000 objects

            for (int i = 0; i < 10000; i++) {
                // Build a sample event to send; make sure we use a current date
                final Map<String, Object> obj = ImmutableMap.<String, Object>of(
                        "timestamp", new DateTime().toString(),
                        "page", "foo",
                        "added", i
                );

                // Asynchronously send event to Druid:
                sender.send(obj).addEventListener(
                        new FutureEventListener<BoxedUnit>() {
                            public void onSuccess(BoxedUnit value) {
                                log.info("Sent message: %s", obj);
                            }

                            public void onFailure(Throwable e) {
                                if (e instanceof MessageDroppedException) {
                                    log.warn(e, "Dropped message: %s", obj);
                                } else {
                                    log.error(e, "Failed to send message: %s", obj);
                                }
                            }
                        }
                );
            }
        } finally {
            sender.flush();
            sender.stop();
        }
    }
}

