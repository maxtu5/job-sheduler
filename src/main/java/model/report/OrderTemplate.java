package model.report;

import lombok.Builder;

import java.util.List;

@Builder
public class OrderTemplate {
        public String orderId;
        public List<OperationTemplate> operations;
}
