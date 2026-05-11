import { useCallback, useRef, useEffect } from 'react';
import { voiceApi } from '../services/api';
import useVoiceStore from '../store/useVoiceStore';

/**
 * useVoice — Custom hook for the complete voice assistant pipeline.
 *
 * Features:
 * - Browser mic permission check before starting
 * - Dual language: Urdu (ur-PK) primary, English (en-US) secondary
 * - Interim results for real-time transcript display
 * - Auto-retry once on no-speech errors
 * - Auto-dismiss overlay 5s after AI response
 * - Proper cleanup on unmount
 * - Mobile-compatible (handles webkit prefix)
 * - TTS with voice selection for Urdu/English
 */
export default function useVoice() {
  const recognitionRef = useRef(null);
  const dismissTimerRef = useRef(null);
  const retryCountRef = useRef(0);

  const {
    setListening, setTranscript, setAiResponse,
    setProcessing, setError, setPermissionStatus,
    setLanguage, addToHistory, dismiss, reset,
    language, aiResponse, transcript,
  } = useVoiceStore();

  // ── Cleanup on unmount ──
  useEffect(() => {
    return () => {
      if (recognitionRef.current) {
        recognitionRef.current.abort();
        recognitionRef.current = null;
      }
      if (dismissTimerRef.current) {
        clearTimeout(dismissTimerRef.current);
      }
    };
  }, []);

  // ── Auto-dismiss overlay 5s after response ──
  useEffect(() => {
    if (aiResponse) {
      if (dismissTimerRef.current) clearTimeout(dismissTimerRef.current);
      dismissTimerRef.current = setTimeout(() => {
        dismiss();
      }, 6000);
    }
    return () => {
      if (dismissTimerRef.current) clearTimeout(dismissTimerRef.current);
    };
  }, [aiResponse, dismiss]);

  // ── Text-to-Speech ──
  const speak = useCallback((text, lang = 'ur') => {
    if (!window.speechSynthesis || !text) return;

    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang === 'ur' ? 'ur-PK' : 'en-US';
    utterance.rate = lang === 'ur' ? 0.85 : 0.95; // Urdu slightly slower
    utterance.pitch = 1.0;
    utterance.volume = 1.0;

    // Try to find a matching voice
    const voices = window.speechSynthesis.getVoices();
    const preferredLang = lang === 'ur' ? 'ur' : 'en';
    const voice = voices.find(v => v.lang.startsWith(preferredLang))
      || voices.find(v => v.lang.startsWith('en')); // Fallback to English
    if (voice) utterance.voice = voice;

    window.speechSynthesis.speak(utterance);
  }, []);

  // ── Send transcript to backend AI ──
  const processText = useCallback(async (text) => {
    if (!text?.trim()) return null;

    setProcessing(true);
    setTranscript(text);

    try {
      const { data } = await voiceApi.sendCommand(text);

      setAiResponse(data);

      // Auto-speak the response in the detected language
      const responseLang = data?.data?.language || language;
      const msg = responseLang === 'ur' ? data?.messageUr : data?.messageEn;
      if (msg) speak(msg, responseLang);

      // Save to history
      addToHistory(text, data);

      return data;
    } catch (err) {
      const errMsg = err.response?.data?.messageEn
        || err.message
        || 'Server not reachable. Please try again.';
      const errMsgUr = err.response?.data?.messageUr
        || 'سرور سے رابطہ نہیں ہو سکا۔ دوبارہ کوشش کریں۔';

      setError(errMsgUr);
      speak(errMsgUr, 'ur');
      return null;
    }
  }, [setProcessing, setTranscript, setAiResponse, setError, addToHistory, speak, language]);

  // ── Check mic permission ──
  const checkPermission = useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setPermissionStatus('unsupported');
      return false;
    }
    try {
      // Check existing permission state if available
      if (navigator.permissions) {
        const perm = await navigator.permissions.query({ name: 'microphone' });
        if (perm.state === 'denied') {
          setPermissionStatus('denied');
          setError('مائیکروفون کی اجازت نہیں ملی۔ براؤزر سیٹنگ میں اجازت دیں۔\nMicrophone access denied. Please allow in browser settings.');
          return false;
        }
      }
      // Actually request mic access
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach(track => track.stop()); // Stop immediately — we just needed the permission
      setPermissionStatus('granted');
      return true;
    } catch (err) {
      if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
        setPermissionStatus('denied');
        setError('مائیکروفون کی اجازت نہیں ملی۔ براؤزر سیٹنگ چیک کریں۔');
      } else if (err.name === 'NotFoundError') {
        setPermissionStatus('unsupported');
        setError('کوئی مائیکروفون نہیں ملا۔ Please connect a microphone.');
      } else {
        setError('Microphone error: ' + err.message);
      }
      return false;
    }
  }, [setPermissionStatus, setError]);

  // ── Start Speech Recognition ──
  const startListening = useCallback(async () => {
    // Check browser support
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setPermissionStatus('unsupported');
      setError('آپ کا براؤزر آواز نہیں سمجھتا۔ Chrome یا Safari استعمال کریں۔');
      return;
    }

    // Request mic permission first
    const hasPermission = await checkPermission();
    if (!hasPermission) return;

    // Abort any existing recognition
    if (recognitionRef.current) {
      recognitionRef.current.abort();
    }

    reset();
    retryCountRef.current = 0;

    const recognition = new SpeechRecognition();
    // Try current language preference, fallback to multi-language
    recognition.lang = language === 'ur' ? 'ur-PK' : 'en-US';
    recognition.interimResults = true;   // Show real-time transcript
    recognition.maxAlternatives = 3;     // Get alternatives for better accuracy
    recognition.continuous = false;

    recognition.onstart = () => {
      setListening(true);
      setError(null);
    };

    recognition.onresult = (event) => {
      let interimText = '';
      let finalText = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        const text = result[0].transcript;
        if (result.isFinal) {
          finalText += text;
        } else {
          interimText += text;
        }
      }

      // Show interim text in real-time
      if (interimText) setTranscript(interimText);

      // Process final result
      if (finalText.trim()) {
        setListening(false);
        processText(finalText.trim());
      }
    };

    recognition.onerror = (event) => {
      console.warn('Speech recognition error:', event.error);

      if (event.error === 'no-speech') {
        // Auto-retry once on no-speech
        if (retryCountRef.current < 1) {
          retryCountRef.current++;
          // Don't show error — just retry silently
          return;
        }
        setError('آواز نہیں سنائی دی۔ دوبارہ کوشش کریں۔');
      } else if (event.error === 'not-allowed') {
        setPermissionStatus('denied');
        setError('مائیکروفون کی اجازت منسوخ کر دی گئی۔');
      } else if (event.error === 'network') {
        setError('نیٹ ورک خرابی۔ انٹرنیٹ کنیکشن چیک کریں۔');
      } else if (event.error === 'audio-capture') {
        setError('مائیکروفون دستیاب نہیں۔ Please check your microphone.');
      } else if (event.error === 'aborted') {
        // User stopped — no error needed
        return;
      } else {
        setError(`آواز کی خرابی: ${event.error}`);
      }

      setListening(false);
    };

    recognition.onend = () => {
      setListening(false);
    };

    recognitionRef.current = recognition;

    try {
      recognition.start();
    } catch (err) {
      setError('Recognition could not start: ' + err.message);
      setListening(false);
    }
  }, [
    language, reset, setListening, setTranscript,
    setError, setPermissionStatus, checkPermission, processText
  ]);

  // ── Stop Listening ──
  const stopListening = useCallback(() => {
    if (recognitionRef.current) {
      recognitionRef.current.stop();
    }
    setListening(false);
  }, [setListening]);

  // ── Toggle Language ──
  const toggleLanguage = useCallback(() => {
    const newLang = language === 'ur' ? 'en' : 'ur';
    setLanguage(newLang);
    speak(
      newLang === 'ur' ? 'اردو میں بات کریں' : 'Speak in English',
      newLang
    );
  }, [language, setLanguage, speak]);

  return {
    startListening,
    stopListening,
    processText,
    speak,
    toggleLanguage,
  };
}
