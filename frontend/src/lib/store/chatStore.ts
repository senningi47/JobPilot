import { create } from "zustand";
import apiClient from "@/lib/api";

export interface ChatMessage {
  id: number;
  role: "user" | "assistant";
  content: string;
  intent?: string;
  modelUsed?: string;
  createdAt: string;
}

export interface ChatSession {
  id: number;
  sessionId: string;
  title: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

interface ChatState {
  sessions: ChatSession[];
  currentSessionId: string | null;
  messages: ChatMessage[];
  loading: boolean;
  sending: boolean;
  fetchSessions: () => Promise<void>;
  createSession: () => Promise<string>;
  selectSession: (sessionId: string) => Promise<void>;
  sendMessage: (content: string) => Promise<void>;
}

const useChatStore = create<ChatState>((set, get) => ({
  sessions: [],
  currentSessionId: null,
  messages: [],
  loading: false,
  sending: false,

  fetchSessions: async () => {
    try {
      const res: any = await apiClient.get("/v1/chat/sessions");
      set({ sessions: res.data.list });
    } catch {
      // silent fail
    }
  },

  createSession: async () => {
    const res: any = await apiClient.post("/v1/chat/sessions");
    const session = res.data;
    set((state) => ({
      sessions: [session, ...state.sessions],
      currentSessionId: session.sessionId,
      messages: [],
    }));
    return session.sessionId;
  },

  selectSession: async (sessionId: string) => {
    set({ loading: true, currentSessionId: sessionId });
    try {
      const res: any = await apiClient.get(
        `/v1/chat/sessions/${sessionId}/messages`
      );
      set({ messages: res.data.list ?? res.data });
    } catch {
      set({ messages: [] });
    } finally {
      set({ loading: false });
    }
  },

  sendMessage: async (content: string) => {
    const { currentSessionId } = get();
    if (!currentSessionId) return;

    const optimisticMsg: ChatMessage = {
      id: Date.now(),
      role: "user",
      content,
      createdAt: new Date().toISOString(),
    };
    set((state) => ({
      messages: [...state.messages, optimisticMsg],
      sending: true,
    }));

    try {
      const res: any = await apiClient.post(
        `/v1/chat/sessions/${currentSessionId}/messages`,
        { content }
      );
      const aiMsg: ChatMessage = res.data;
      set((state) => ({ messages: [...state.messages, aiMsg] }));
    } catch {
      const errorMsg: ChatMessage = {
        id: Date.now() + 1,
        role: "assistant",
        content: "抱歉，消息发送失败，请稍后重试。",
        createdAt: new Date().toISOString(),
      };
      set((state) => ({ messages: [...state.messages, errorMsg] }));
    } finally {
      set({ sending: false });
    }
  },
}));

export default useChatStore;
