/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.hpcloud.mon.infrastructure.persistence.influxdb;

import com.google.inject.Inject;

import com.hpcloud.mon.MonApiConfiguration;
import com.hpcloud.mon.domain.model.statistic.StatisticRepository;
import com.hpcloud.mon.domain.model.statistic.Statistics;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Serie;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import static com.hpcloud.mon.infrastructure.persistence.influxdb.Utils.buildSerieNameRegex;

public class StatisticInfluxDbRepositoryImpl implements StatisticRepository {

  private static final Logger logger = LoggerFactory
      .getLogger(StatisticInfluxDbRepositoryImpl.class);

  private final MonApiConfiguration config;
  private final InfluxDB influxDB;

  public static final DateTimeFormatter DATETIME_FORMATTER = ISODateTimeFormat.dateTimeNoMillis()
      .withZoneUTC();

  @Inject
  public StatisticInfluxDbRepositoryImpl(MonApiConfiguration config, InfluxDB influxDB) {
    this.config = config;
    this.influxDB = influxDB;
  }

  @Override
  public List<Statistics> find(String tenantId, String name, Map<String, String> dimensions,
                               DateTime startTime, @Nullable DateTime endTime,
                               List<String> statistics, int period)
      throws Exception {

    String serieNameRegex = buildSerieNameRegex(tenantId, name, dimensions);
    String statsPart = buildStatsPart(statistics);
    String timePart = Utils.WhereClauseBuilder.buildTimePart(startTime, endTime);
    String periodPart = buildPeriodPart(period);

    String query =
        String.format("select time %1$s from /%2$s/ where 1=1 %3$s %4$s",
                      statsPart, serieNameRegex, timePart, periodPart);
    logger.debug("Query string: {}", query);

    List<Serie> result =
        this.influxDB.Query(this.config.influxDB.getName(), query, TimeUnit.MILLISECONDS);

    List<Statistics> statisticsList = new LinkedList<Statistics>();

    for (Serie serie : result) {
      Utils.SerieNameConverter serieNameConverter = new Utils.SerieNameConverter(serie.getName());
      Statistics statistic = new Statistics();
      statistic.setName(serieNameConverter.getMetricName());
      List<String> colNamesList = new LinkedList<>(statistics);
      colNamesList.add(0, "timestamp");
      statistic.setColumns(colNamesList);
      statistic.setDimensions(serieNameConverter.getDimensions());
      List<List<Object>> valObjArryArry = new LinkedList<List<Object>>();
      statistic.setStatistics(valObjArryArry);
      final String[] colNames = serie.getColumns();
      final List<Map<String, Object>> rows = serie.getRows();
      for (Map<String, Object> row : rows) {
        List<Object> valObjArry = new ArrayList<>();
        // First column is always time.
        Double timeDouble = (Double) row.get(colNames[0]);
        valObjArry.add(DATETIME_FORMATTER.print(timeDouble.longValue()));
        for (int j = 1; j < statistics.size() + 1; j++) {
          valObjArry.add(row.get(colNames[j]));
        }
        valObjArryArry.add(valObjArry);
      }
      statisticsList.add(statistic);
    }
    return statisticsList;
  }

  private String buildPeriodPart(int period) {
    String s = "";
    if (period >= 1) {
      s += String.format("group by time(%1$ds)", period);
    }

    return s;
  }

  private String buildStatsPart(List<String> statistics) {
    String s = "";

    for (String statistic : statistics) {
      s += ",";
      if (statistic.trim().toLowerCase().equals("avg")) {
        s += " mean(value)";
      } else {
        s += " " + statistic + "(value)";
      }
    }

    return s;
  }
}
