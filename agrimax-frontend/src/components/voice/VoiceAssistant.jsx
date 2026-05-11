import { useEffect } from 'react';
import { HiMicrophone, HiStop, HiXMark, HiLanguage } from 'react-icons/hi2';
import useVoice from '../../hooks/useVoice';
import useVoiceStore from '../../store/useVoiceStore';

/**
 * VoiceAssistant — Floating mic button + overlay UI.
 *
 * Improvements:
 * - Close/dismiss button on overlay
 * - Auto-dismiss after 6s of showing response
 * - Permission denied error state with clear guidance
 * - Language toggle (Urdu ↔ English)
 * - Richer listening animation (7 bars with staggered color)
 * - Mic status indicator on button
 * - Processing dots animation
 */
export default function VoiceAssistant() {
  const { startListening, stopListening, toggleLanguage } = useVoice();
  const {
    isListening, transcript, aiResponse, isProcessing,
    error, permissionStatus, language, dismiss,
  } = useVoiceStore();

  const showOverlay = isListening || isProcessing || !!transcript || !!aiResponse || !!error;

  return (
    <>
      {/* ── Overlay ── */}
      {showOverlay && (
        <div
          className="fixed inset-0 bg-black/60 backdrop-blur-sm z-40 flex items-end justify-center pb-28"
          onClick={(e) => {
            // Dismiss when clicking the backdrop (not the card)
            if (e.target === e.currentTarget) dismiss();
          }}
        >
          <div className="glass-card p-5 mx-4 w-full max-w-md animate-fade-up relative">

            {/* Close button */}
            <button
              onClick={dismiss}
              className="absolute top-3 right-3 p-1.5 rounded-full hover:bg-slate-700/60 transition-colors text-slate-400 hover:text-white"
              aria-label="Close"
            >
              <HiXMark className="text-xl" />
            </button>

            {/* Language badge */}
            <div className="absolute top-3 left-3">
              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                language === 'ur'
                  ? 'bg-green-500/20 text-green-400'
                  : 'bg-blue-500/20 text-blue-400'
              }`}>
                {language === 'ur' ? '🇵🇰 اردو' : '🇺🇸 English'}
              </span>
            </div>

            <div className="mt-6">
              {/* Listening Animation */}
              {isListening && (
                <div className="text-center mb-4">
                  <div className="flex items-center justify-center gap-1 h-14 mb-3">
                    {[0, 1, 2, 3, 4, 5, 6].map((i) => (
                      <div
                        key={i}
                        className="listening-bar"
                        style={{
                          height: '100%',
                          animationDelay: `${i * 0.12}s`,
                          opacity: 0.6 + (i % 3) * 0.15,
                        }}
                      />
                    ))}
                  </div>
                  <p className="text-green-400 text-lg font-semibold urdu-text">
                    🎤 سن رہا ہوں...
                  </p>
                  <p className="text-slate-400 text-sm mt-1">Listening...</p>
                </div>
              )}

              {/* Real-time transcript (interim) */}
              {transcript && (
                <div className="mb-4 animate-slide-in">
                  <p className="text-slate-400 text-xs mb-1 urdu-text">آپ نے کہا:</p>
                  <p className="text-white text-lg font-medium leading-relaxed">
                    &ldquo;{transcript}&rdquo;
                  </p>
                </div>
              )}

              {/* Processing indicator */}
              {isProcessing && (
                <div className="flex items-center gap-3 text-amber-400 animate-slide-in mb-4">
                  <div className="flex gap-1">
                    {[0, 1, 2].map((i) => (
                      <div
                        key={i}
                        className="w-2 h-2 rounded-full bg-amber-400 animate-bounce"
                        style={{ animationDelay: `${i * 0.15}s` }}
                      />
                    ))}
                  </div>
                  <span className="urdu-text">سوچ رہا ہوں...</span>
                  <span className="text-slate-400 text-sm">Processing</span>
                </div>
              )}

              {/* AI Response */}
              {aiResponse && !isProcessing && (
                <div className="animate-slide-in">
                  <div className={`p-4 rounded-xl ${
                    aiResponse.success
                      ? 'bg-green-500/10 border border-green-500/30'
                      : 'bg-amber-500/10 border border-amber-500/30'
                  }`}>
                    <p className={`text-lg font-semibold urdu-text leading-relaxed ${
                      aiResponse.success ? 'text-green-400' : 'text-amber-400'
                    }`}>
                      {aiResponse.success ? '✅' : '⚠️'}{' '}
                      {aiResponse.messageUr || aiResponse.messageEn}
                    </p>
                    {aiResponse.messageEn && aiResponse.messageUr && (
                      <p className="text-slate-400 text-sm mt-2">
                        {aiResponse.messageEn}
                      </p>
                    )}
                    {/* Intent badge */}
                    {aiResponse.data?.intent && (
                      <span className="mt-2 inline-block text-[10px] bg-slate-700/60 text-slate-400 px-2 py-0.5 rounded-full">
                        {aiResponse.data.intent} • {Math.round((aiResponse.data.confidence || 0) * 100)}%
                      </span>
                    )}
                  </div>
                </div>
              )}

              {/* Permission Denied Error */}
              {permissionStatus === 'denied' && (
                <div className="p-4 rounded-xl bg-red-500/10 border border-red-500/30 animate-slide-in">
                  <p className="text-red-400 font-medium urdu-text">
                    🔒 مائیکروفون کی اجازت نہیں
                  </p>
                  <p className="text-slate-400 text-sm mt-1">
                    Browser settings → Site permissions → Allow microphone
                  </p>
                </div>
              )}

              {/* Generic Error */}
              {error && permissionStatus !== 'denied' && (
                <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 animate-slide-in">
                  <p className="text-red-400 urdu-text">❌ {error}</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ── Language Toggle Button ── */}
      <button
        onClick={toggleLanguage}
        className="fixed bottom-24 right-6 z-50 w-10 h-10 rounded-full flex items-center justify-center text-slate-400 hover:text-white bg-slate-800/80 border border-slate-700/50 hover:bg-slate-700/80 transition-all shadow-lg"
        aria-label="Toggle language"
        title={language === 'ur' ? 'Switch to English' : 'اردو میں بدلیں'}
      >
        <HiLanguage className="text-lg" />
      </button>

      {/* ── Floating Mic Button ── */}
      <button
        id="voice-mic-button"
        onClick={isListening ? stopListening : startListening}
        disabled={isProcessing}
        className={`fixed bottom-6 right-6 z-50 w-16 h-16 rounded-full flex items-center justify-center text-white text-2xl shadow-2xl transition-all duration-300 disabled:opacity-60 ${
          isListening
            ? 'bg-red-500 animate-mic-pulse scale-110'
            : isProcessing
            ? 'bg-amber-600 cursor-not-allowed'
            : 'bg-green-600 hover:bg-green-500 hover:scale-105 active:scale-95'
        }`}
        aria-label={isListening ? 'Stop listening' : 'Start voice command'}
      >
        {isListening
          ? <HiStop className="text-2xl" />
          : isProcessing
          ? <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
          : <HiMicrophone className="text-2xl" />
        }
      </button>

      {/* ── Mic status dot ── */}
      {permissionStatus === 'denied' && (
        <div className="fixed bottom-20 right-6 z-50 w-2.5 h-2.5 bg-red-500 rounded-full border-2 border-slate-900" />
      )}
      {permissionStatus === 'granted' && !isListening && (
        <div className="fixed bottom-20 right-6 z-50 w-2.5 h-2.5 bg-green-500 rounded-full border-2 border-slate-900" />
      )}
    </>
  );
}
