"use client";

import ReactECharts from "echarts-for-react";

interface SalaryData {
  p25: number;
  p50: number;
  p75: number;
  currency: string;
}

interface SalaryChartProps {
  salaryData: SalaryData;
}

export default function SalaryChart({ salaryData }: SalaryChartProps) {
  const option = {
    title: {
      text: "薪资分布",
      left: "center",
      textStyle: { fontSize: 14 },
    },
    tooltip: {
      trigger: "axis" as const,
      formatter: (params: { name: string; value: number }[]) => {
        const item = params[0];
        return `${item.name}: ${salaryData.currency} ${item.value.toLocaleString()}`;
      },
    },
    grid: {
      left: "10%",
      right: "10%",
      bottom: "10%",
      top: "18%",
    },
    xAxis: {
      type: "category" as const,
      data: ["P25", "P50", "P75"],
      axisTick: { show: false },
    },
    yAxis: {
      type: "value" as const,
      axisLabel: {
        formatter: (val: number) => `${salaryData.currency} ${val.toLocaleString()}`,
      },
    },
    series: [
      {
        type: "bar",
        data: [
          {
            value: salaryData.p25,
            itemStyle: { color: "#93c5fd" },
          },
          {
            value: salaryData.p50,
            itemStyle: { color: "#3b82f6" },
          },
          {
            value: salaryData.p75,
            itemStyle: { color: "#1d4ed8" },
          },
        ],
        barWidth: "40%",
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
