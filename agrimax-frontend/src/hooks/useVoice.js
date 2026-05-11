import { useCallback, useRef } from 'react';
import { voiceApi } from '../services/api';
import useVoiceStore from '../store/useVoiceStore';

/**
 * Custom hook for the voice assistant — handles Web Speech API + backend AI integration.
 * Provides: startListening, stopListening, processText, speak
 */
export default function useVoice() {
  const recognitionRef = useRef(null);
  const {
    setListening, setTranscript, setAiResponse, setProcessing, setError, reset,
  } = useVoiceStore();

  // ── Text-to-Speech (browser native) ──
  const speak = useCallback((text, lang = 'ur') => {
    if (!window.speechSynthesis) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang === 'ur' ? 'ur-PK' : 'en-US';
    utterance.rate = 0.9;
    window.speechSynthesis.speak(utterance);
  }, []);

  // ── Process text through backend AI ──
  const processText = useCallback(async (text) => {
    if (!text?.trim()) return null;
    setProcessing(true);
    setTranscript(text);
    try {
      const { data } = await voiceApi.sendCommand(text);
      setAiResponse(data);
      // Speak the response in detected language
      const lang = data?.data?.language || 'ur';
      const msg = lang === 'ur' ? data?.messageUr : data?.messageEn;
      if (msg) speak(msg, lang);
      return data;
    } catch (err) {
      const errMsg = err.response?.data?.messageEn || 'Something went wrong';
      setError(errMsg);
      speak('Sorry, kuch galat ho gaya. Dobara koshish karein.', 'ur');
      return null;
    }
  }, [setProcessing, setTranscript, setAiResponse, setError, speak]);

  // ── Start Speech Recognition ──
  const startListening = useCallback(() => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setError('Speech recognition is not supported in this browser');
      return;
    }

    reset();
    const recognition = new SpeechRecognition();
    recognition.lang = 'ur-PK';
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;
    recognition.continuous = false;

    recognition.onstart = () => setListening(true);

    recognition.onresult = (event) => {
      const result = event.results[event.results.length - 1];
      const text = result[0].transcript;
      setTranscript(text);
      if (result.isFinal) {
        setListening(false);
        processText(text);
      }
    };

    recognition.onerror = (event) => {
      console.error('Speech error:', event.error);
      if (event.error !== 'no-speech') {
        setError('آواز نہیں سن سکا۔ دوبارہ کوشش کریں');
      }
      setListening(false);
    };

    recognition.onend = () => setListening(false);

    recognitionRef.current = recognition;
    recognition.start();
  }, [reset, setListening, setTranscript, setError, processText]);

  // ── Stop Listening ──
  const stopListening = useCallback(() => {
    if (recognitionRef.current) {
      recognitionRef.current.stop();
    }
    setListening(false);
  }, [setListening]);

  return { startListening, stopListening, processText, speak };
}
