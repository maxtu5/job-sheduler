package model.report;

import lombok.Builder;

@Builder
public class OrderBatchDataTemplate {

    public int orders;
    public int orderTypes;
    public String makespan;

}
