package com.omtb.backend.models;
import lombok.Data;
import java.util.List;

@Data
public class ScheduleDto {
    private String date;
    private List<TimingDto> timings;
}
