package model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import model.StepType;

@Getter
@Setter
@AllArgsConstructor
public class StationDto {
    private StepType operation;
    private String name;
    private int capacity;

}
