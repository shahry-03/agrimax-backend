import { create } from 'zustand';

const useVoiceStore = create((set) => ({
  isListening: false,
  transcript: '',
  aiResponse: null,
  isProcessing: false,
  error: null,

  setListening: (isListening) => set({ isListening }),
  setTranscript: (transcript) => set({ transcript }),
  setAiResponse: (aiResponse) => set({ aiResponse, isProcessing: false }),
  setProcessing: (isProcessing) => set({ isProcessing }),
  setError: (error) => set({ error, isProcessing: false, isListening: false }),
  reset: () => set({ isListening: false, transcript: '', aiResponse: null, isProcessing: false, error: null }),
}));

export default useVoiceStore;
