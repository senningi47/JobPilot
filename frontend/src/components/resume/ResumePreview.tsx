"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

interface Education {
  school?: string;
  degree?: string;
  major?: string;
  start_date?: string;
  end_date?: string;
}

interface Project {
  name?: string;
  description?: string;
  tech_stack?: string[];
}

interface StructuredData {
  basic_info?: {
    name?: string;
    email?: string;
    target_position?: string;
    phone?: string;
  };
  education?: Education[];
  skills?: string[];
  projects?: Project[];
}

interface ResumePreviewProps {
  data: StructuredData;
}

export default function ResumePreview({ data }: ResumePreviewProps) {
  const basic = data.basic_info;

  return (
    <div className="space-y-4">
      {/* Basic Info */}
      <Card>
        <CardHeader>
          <CardTitle>基本信息</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
            {basic?.name && (
              <>
                <dt className="text-muted-foreground">姓名</dt>
                <dd>{basic.name}</dd>
              </>
            )}
            {basic?.email && (
              <>
                <dt className="text-muted-foreground">邮箱</dt>
                <dd>{basic.email}</dd>
              </>
            )}
            {basic?.phone && (
              <>
                <dt className="text-muted-foreground">电话</dt>
                <dd>{basic.phone}</dd>
              </>
            )}
            {basic?.target_position && (
              <>
                <dt className="text-muted-foreground">目标岗位</dt>
                <dd>{basic.target_position}</dd>
              </>
            )}
          </dl>
        </CardContent>
      </Card>

      {/* Education */}
      {data.education && data.education.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>教育经历</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {data.education.map((edu, i) => (
                <div key={i} className="text-sm">
                  <div className="flex items-center justify-between">
                    <span className="font-medium">
                      {edu.school || "未知学校"}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {edu.start_date && edu.end_date
                        ? `${edu.start_date} — ${edu.end_date}`
                        : edu.start_date || ""}
                    </span>
                  </div>
                  {(edu.degree || edu.major) && (
                    <p className="text-muted-foreground mt-0.5">
                      {[edu.degree, edu.major].filter(Boolean).join(" · ")}
                    </p>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Skills */}
      {data.skills && data.skills.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>技能</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {data.skills.map((skill, i) => (
                <Badge key={i} variant="secondary">
                  {skill}
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Projects */}
      {data.projects && data.projects.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>项目经历</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {data.projects.map((proj, i) => (
                <div key={i} className="text-sm">
                  <p className="font-medium">
                    {proj.name || "未命名项目"}
                  </p>
                  {proj.description && (
                    <p className="text-muted-foreground mt-1">
                      {proj.description}
                    </p>
                  )}
                  {proj.tech_stack && proj.tech_stack.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mt-2">
                      {proj.tech_stack.map((tech, j) => (
                        <Badge key={j} variant="outline">
                          {tech}
                        </Badge>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
