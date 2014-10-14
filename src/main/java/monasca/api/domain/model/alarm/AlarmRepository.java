package monasca.api.domain.model.alarm;

import java.util.List;
import java.util.Map;

import monasca.common.model.alarm.AlarmState;
import monasca.common.model.alarm.AlarmSubExpression;
import monasca.common.model.metric.MetricDefinition;
import monasca.api.domain.exception.EntityNotFoundException;

public interface AlarmRepository {
  /**
   * Deletes all alarms associated with the {@code id}.
   */
  void deleteById(String id);

  /**
   * Returns alarms for the given criteria.
   */
  List<Alarm> find(String tenantId, String alarmDefId, String metricName,
      Map<String, String> metricDimensions, AlarmState state);

  /**
   * @throws EntityNotFoundException if an alarm cannot be found for the {@code id}
   */
  Alarm findById(String id);

  List<MetricDefinition> findMetrics(String alarmId);

  /**
   * Updates and returns an alarm for the criteria.
   */
  void update(String tenantId, String id, AlarmState state);

  /**
   * Gets the AlarmSubExpressions mapped by their Ids for an Alarm Id
   */
  Map<String, AlarmSubExpression> findAlarmSubExpressions(String alarmId);
}
