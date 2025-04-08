package model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Builder
@Getter
@Setter
public class WorkUnit {

    private String orderId;
    private StepType stepType;
    private LocalTime beginTime;
    private double duration;

}
