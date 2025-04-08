package model;

import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class OrderType {

    public String name;
    public List<Step> steps;

}
