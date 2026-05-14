"use client";

import { useEffect } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import useChatStore from "@/lib/store/chatStore";

export default function ChatSidebar() {
  const { sessions, currentSessionId, fetchSessions, createSession, selectSession } =
    useChatStore();

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions]);

  const handleNew = async () => {
    await createSession();
  };

  return (
    <div className="flex h-full w-64 flex-col border-r bg-gray-50">
      <div className="p-3">
        <Button onClick={handleNew} className="w-full" variant="outline">
          + 新对话
        </Button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {sessions.map((session) => (
          <button
            key={session.sessionId}
            onClick={() => selectSession(session.sessionId)}
            className={cn(
              "w-full truncate px-4 py-2.5 text-left text-sm transition-colors hover:bg-gray-100",
              session.sessionId === currentSessionId && "bg-gray-200 font-medium"
            )}
          >
            {session.title || "新对话"}
          </button>
        ))}
      </div>
    </div>
  );
}
