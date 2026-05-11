import { HiMicrophone, HiStop } from 'react-icons/hi2';
import useVoice from '../../hooks/useVoice';
import useVoiceStore from '../../store/useVoiceStore';

/**
 * Floating voice assistant — the heart of the voice-first experience.
 * Shows a large mic button, listening animation, transcript, and AI response.
 */
export default function VoiceAssistant() {
  const { startListening, stopListening } = useVoice();
  const { isListening, transcript, aiResponse, isProcessing, error } = useVoiceStore();

  return (
    <>
      {/* Overlay when active */}
      {(isListening || isProcessing || transcript) && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-40 flex items-end justify-center pb-32">
          <div className="glass-card p-6 mx-4 w-full max-w-md animate-fade-up">
            {/* Listening animation */}
            {isListening && (
              <div className="text-center mb-4">
                <div className="flex items-center justify-center gap-1 h-12 mb-3">
                  {[0, 1, 2, 3, 4, 5, 6].map((i) => (
                    <div
                      key={i}
                      className="listening-bar"
                      style={{
                        height: '100%',
                        animationDelay: `${i * 0.1}s`,
                      }}
                    />
                  ))}
                </div>
                <p className="text-green-400 text-lg font-medium">🎤 سن رہا ہوں...</p>
                <p className="text-slate-400 text-sm mt-1">Listening...</p>
              </div>
            )}

            {/* Transcript */}
            {transcript && (
              <div className="mb-4 animate-slide-in">
                <p className="text-slate-400 text-xs mb-1">آپ نے کہا:</p>
                <p className="text-white text-lg font-medium">"{transcript}"</p>
              </div>
            )}

            {/* Processing */}
            {isProcessing && (
              <div className="flex items-center gap-3 text-amber-400 animate-slide-in">
                <div className="w-5 h-5 border-2 border-amber-400 border-t-transparent rounded-full animate-spin" />
                <span>سوچ رہا ہوں... Processing...</span>
              </div>
            )}

            {/* AI Response */}
            {aiResponse && !isProcessing && (
              <div className="animate-slide-in">
                <div className={`p-4 rounded-xl ${aiResponse.success ? 'bg-green-500/10 border border-green-500/30' : 'bg-red-500/10 border border-red-500/30'}`}>
                  <p className={`text-lg font-medium ${aiResponse.success ? 'text-green-400' : 'text-red-400'}`}>
                    {aiResponse.success ? '✅' : '❌'} {aiResponse.messageUr || aiResponse.messageEn}
                  </p>
                  {aiResponse.messageEn && (
                    <p className="text-slate-400 text-sm mt-1">{aiResponse.messageEn}</p>
                  )}
                </div>
              </div>
            )}

            {/* Error */}
            {error && (
              <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 animate-slide-in">
                <p className="text-red-400">❌ {error}</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Floating Mic Button */}
      <button
        onClick={isListening ? stopListening : startListening}
        className={`fixed bottom-6 right-6 z-50 w-16 h-16 rounded-full flex items-center justify-center text-white text-2xl shadow-2xl transition-all duration-300 ${
          isListening
            ? 'bg-red-500 animate-mic-pulse scale-110'
            : 'bg-green-600 hover:bg-green-500 hover:scale-105'
        }`}
        aria-label={isListening ? 'Stop listening' : 'Start voice command'}
      >
        {isListening ? <HiStop className="text-2xl" /> : <HiMicrophone className="text-2xl" />}
      </button>
    </>
  );
}
