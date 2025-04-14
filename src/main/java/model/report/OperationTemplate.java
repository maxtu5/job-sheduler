package model.report;

import lombok.Builder;
import model.StepType;

import java.time.LocalDate;

@Builder
public class OperationTemplate {
    public StepType operation;
    public String date;
    public String place;
    public String time;
}
