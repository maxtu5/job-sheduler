package model.report;

import lombok.Builder;
import model.StepType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Builder
public class ReportTemplate {

    public OrderBatchDataTemplate execution;
    public List<OrderTemplate> orders;
    public Map<StepType, List<StationTemplate>> stations;

}
