package model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
public class OrderDto {

    private String orderId;
    private String type;
    private LocalDate dueDate;

}
