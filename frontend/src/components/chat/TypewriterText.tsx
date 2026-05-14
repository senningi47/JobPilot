"use client";

import { useState, useEffect } from "react";
import ReactMarkdown from "react-markdown";

interface TypewriterTextProps {
  content: string;
  speed?: number;
}

export default function TypewriterText({ content, speed = 20 }: TypewriterTextProps) {
  const [displayed, setDisplayed] = useState("");
  const [done, setDone] = useState(false);

  useEffect(() => {
    setDisplayed("");
    setDone(false);
    let i = 0;
    const interval = setInterval(() => {
      i++;
      if (i >= content.length) {
        setDisplayed(content);
        setDone(true);
        clearInterval(interval);
      } else {
        setDisplayed(content.slice(0, i));
      }
    }, speed);
    return () => clearInterval(interval);
  }, [content, speed]);

  if (done) {
    return <ReactMarkdown>{content}</ReactMarkdown>;
  }

  return (
    <span className="whitespace-pre-wrap">
      {displayed}
      <span className="animate-pulse">|</span>
    </span>
  );
}
