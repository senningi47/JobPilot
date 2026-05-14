"use client";

import useChatStore from "@/lib/store/chatStore";
import ChatMessageList from "@/components/chat/ChatMessageList";
import ChatInput from "@/components/chat/ChatInput";

export default function ChatPage() {
  const { messages, loading, sending, currentSessionId, createSession, sendMessage } =
    useChatStore();

  const handleSend = async (content: string) => {
    if (!currentSessionId) {
      const newId = await createSession();
      // After creating session, sendMessage will use the new currentSessionId
      // We need to wait for state update, so call sendMessage directly with the new session
      // The store already sets currentSessionId in createSession
      await sendMessage(content);
      return;
    }
    await sendMessage(content);
  };

  return (
    <>
      <ChatMessageList messages={messages} loading={loading} />
      <ChatInput onSend={handleSend} disabled={sending} />
    </>
  );
}
