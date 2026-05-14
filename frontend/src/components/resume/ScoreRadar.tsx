"use client";

import ReactECharts from "echarts-for-react";

interface Dimension {
  name: string;
  score: number;
  feedback: string;
}

interface ScoreRadarProps {
  dimensions: Dimension[];
  overallMatch: number;
}

export default function ScoreRadar({ dimensions, overallMatch }: ScoreRadarProps) {
  const option = {
    title: {
      text: `整体匹配度: ${overallMatch}%`,
      left: "center",
      textStyle: { fontSize: 14 },
    },
    tooltip: {
      trigger: "item" as const,
    },
    radar: {
      indicator: dimensions.map((d) => ({
        name: d.name,
        max: 100,
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
            name: "匹配度",
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
      style={{ height: 300, width: "100%" }}
      opts={{ renderer: "svg" }}
    />
  );
}
