/*
 * Copyright (c) 2016 Uber Technologies, Inc. (hoodie-dev-group@uber.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.hoodie.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Closeables;
import com.uber.hoodie.config.HoodieWriteConfig;
import com.uber.hoodie.exception.HoodieException;
import java.io.Closeable;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * This is the main class of the metrics system.
 */
public class Metrics {
  private static Logger logger = LogManager.getLogger(Metrics.class);

  private static volatile boolean initialized = false;
  private static Metrics metrics = null;
  private static HoodieWriteConfig config;
  private final MetricRegistry registry;
  private MetricsReporter reporter = null;

  private Metrics(HoodieWriteConfig metricConfig) throws ConfigurationException {
    config = metricConfig;
    registry = new MetricRegistry();

    reporter = MetricsReporterFactory.createReporter(metricConfig, registry);
    if (reporter == null) {
      throw new RuntimeException("Cannot initialize Reporter.");
    }
    // reporter.start();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          reporter.report();
          Closeables.close(reporter.getReporter(), true);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  public static Metrics getInstance() {
    assert initialized;
    return metrics;
  }

  public static synchronized void init(HoodieWriteConfig metricConfig) {
    if (initialized) {
      return;
    }
    try {
      metrics = new Metrics(metricConfig);
    } catch (ConfigurationException e) {
      throw new HoodieException(e);
    }
    initialized = true;
  }

  public static void registerGauge(String metricName, final long value) {
    switch (config.getMetricsReporterType()) {
      case GRAPHITE:
        try {
          MetricRegistry registry = Metrics.getInstance().getRegistry();
          registry.register(metricName, (Gauge<Long>) () -> value);
        } catch (Exception e) {
          // Here we catch all exception, so the major upsert pipeline will not be affected if the
          // metrics system
          // has some issues.
          logger.error("Failed to send metrics: ", e);
        }
        break;
      case UDP:
        try {
          String[] metrics = metricName.split("\\.");
          String message;
          if (config.getUdpMetricLabels() != null && config.getUdpMetricLabels().length() != 0) {
            message = String.format("%s|g|table_type=%s;table=%s;action=%s;metric=%s;%s|%d",
                    config.getUdpMetricPrefix(), config.getHoodieTableType().name(), metrics[0], metrics[1], metrics[2],
                    config.getUdpMetricLabels(), value);
          } else {
            message = String.format("%s|g|table_type=%s;table=%s;action=%s;metric=%s|%d",
                    config.getUdpMetricPrefix(), config.getHoodieTableType().name(), metrics[0], metrics[1], metrics[2],
                    value);
          }
          Metrics.getInstance().sendToUdp(message);
        } catch (Exception e) {
          logger.error(String.format("Failed to send metrics: {%s, %d}", metricName, value), e);
        }
        break;
    }
  }

  public MetricRegistry getRegistry() {
    return registry;
  }

  public Closeable getReporter() {
    return reporter.getReporter();
  }

  public void sendToUdp(String message) {
    ((MetricsUdpReporter) reporter).sendMessage(message);
  }
}
