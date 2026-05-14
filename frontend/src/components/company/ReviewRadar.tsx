"use client";

import ReactECharts from "echarts-for-react";

interface ReviewDimension {
  name: string;
  score: number;
  summary?: string;
}

interface ReviewRadarProps {
  dimensions: ReviewDimension[];
}

export default function ReviewRadar({ dimensions }: ReviewRadarProps) {
  const option = {
    title: {
      text: "员工评价",
      left: "center",
      textStyle: { fontSize: 14 },
    },
    tooltip: {
      trigger: "item" as const,
    },
    radar: {
      indicator: dimensions.map((d) => ({
        name: d.name,
        max: 5,
      })),
      shape: "circle" as const,
      splitNumber: 5,
      axisName: {
        fontSize: 12,
      },
    },
    series: [
      {
        type: "radar",
        data: [
          {
            value: dimensions.map((d) => d.score),
            name: "评价得分",
            areaStyle: { opacity: 0.2 },
            lineStyle: { color: "#3b82f6", width: 2 },
            itemStyle: { color: "#3b82f6" },
          },
        ],
      },
    ],
  };

  return (
    <ReactECharts
      option={option}
      style={{ height: 280, width: "100%" }}
      opts={{ renderer: "svg" }}
    />
  );
}
