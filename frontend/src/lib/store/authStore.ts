import { create } from "zustand";
import apiClient from "@/lib/api";

interface User {
  id: number;
  username: string;
  email: string;
  major?: string;
}

interface AuthState {
  token: string | null;
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  register: (data: {
    username: string;
    email: string;
    password: string;
    major?: string;
  }) => Promise<void>;
  logout: () => void;
  loadFromStorage: () => void;
}

const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,

  login: async (email: string, password: string) => {
    const res: any = await apiClient.post("/v1/auth/login", {
      email,
      password,
    });
    const { token, user } = res.data;
    localStorage.setItem("token", token);
    set({ token, user });
  },

  register: async (data) => {
    const res: any = await apiClient.post("/v1/auth/register", data);
    const { token, user } = res.data;
    localStorage.setItem("token", token);
    set({ token, user });
  },

  logout: () => {
    localStorage.removeItem("token");
    set({ token: null, user: null });
  },

  loadFromStorage: () => {
    if (typeof window === "undefined") return;
    const token = localStorage.getItem("token");
    if (token) {
      set({ token });
      apiClient
        .get("/v1/auth/me")
        .then((res: any) => {
          set({ user: res.data });
        })
        .catch(() => {
          localStorage.removeItem("token");
          set({ token: null, user: null });
        });
    }
  },
}));

export default useAuthStore;
