package com.omtb.backend.models;
import lombok.Data;
import java.util.List;

@Data
public class ScreenDto {
    private String screenId;
    private String screenName;
    private Integer capacity;
    private String screenType;
    private List<ScheduleDto> schedules;
}
