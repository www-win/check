package com.studybuddy.checkin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class HeatmapResp {
    private int year;
    private List<String> signed;
    private List<String> makeup;
    private HeatmapSummary summary;
}
