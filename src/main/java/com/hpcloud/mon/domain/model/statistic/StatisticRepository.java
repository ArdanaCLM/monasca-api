package com.hpcloud.mon.domain.model.statistic;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

//import com.hpcloud.util.stats.Statistics;

/**
 * Repository for statistics.
 * 
 * @author Jonathan Halterman
 */
public interface StatisticRepository {
  /**
   * Finds statistics for the given criteria.
   */
  List<Statistics> find(String tenantId, String name, Map<String, String> dimensions,
      DateTime startTime, @Nullable DateTime endTime, List<String> statistics, int period);
}
