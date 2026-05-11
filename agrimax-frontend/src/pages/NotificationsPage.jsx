import { useState, useEffect } from 'react';
import { HiBell, HiCheckCircle, HiInformationCircle, HiExclamationTriangle } from 'react-icons/hi2';

// Mock notifications — replace with real API call when backend notification endpoint is ready
const MOCK_NOTIFICATIONS = [
  {
    id: '1',
    type: 'market',
    icon: '📊',
    titleUr: 'گندم کی قیمت بڑھ گئی',
    titleEn: 'Wheat Price Increased',
    bodyUr: 'آج گندم کی اوسط قیمت ₨3200 فی کوئنٹل ہوگئی',
    bodyEn: 'Wheat average price reached ₨3,200 per quintal today',
    time: '2 گھنٹے پہلے',
    isRead: false,
  },
  {
    id: '2',
    type: 'tip',
    icon: '💡',
    titleUr: 'موسم کا مشورہ',
    titleEn: 'Weather Advisory',
    bodyUr: 'اگلے 3 دن بارش کا امکان ہے۔ فصل کاٹنے کا منصوبہ بنائیں',
    bodyEn: 'Rain expected in next 3 days. Plan your harvest accordingly.',
    time: '5 گھنٹے پہلے',
    isRead: false,
  },
  {
    id: '3',
    type: 'success',
    icon: '✅',
    titleUr: 'لاگ ان کامیاب',
    titleEn: 'Login Successful',
    bodyUr: 'آپ کا AgriMax اکاؤنٹ محفوظ طریقے سے کھل گیا',
    bodyEn: 'Your AgriMax account logged in securely',
    time: 'آج',
    isRead: true,
  },
];

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState(MOCK_NOTIFICATIONS);
  const [filter, setFilter] = useState('all'); // 'all' | 'unread'

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  const markAllRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
  };

  const markRead = (id) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
    );
  };

  const filtered = filter === 'unread'
    ? notifications.filter((n) => !n.isRead)
    : notifications;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold urdu-text flex items-center gap-2">
            🔔 اطلاعات
            {unreadCount > 0 && (
              <span className="text-xs bg-red-500 text-white px-2 py-0.5 rounded-full font-medium">
                {unreadCount}
              </span>
            )}
          </h2>
          <p className="text-slate-400 text-xs">Notifications</p>
        </div>
        {unreadCount > 0 && (
          <button
            onClick={markAllRead}
            className="text-xs text-green-400 hover:text-green-300 px-3 py-1.5 rounded-lg hover:bg-green-500/10 transition-colors"
          >
            سب پڑھ لیں ✓
          </button>
        )}
      </div>

      {/* Filter tabs */}
      <div className="flex gap-2">
        {['all', 'unread'].map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all ${
              filter === f
                ? 'bg-green-600 text-white'
                : 'bg-slate-800 text-slate-400 hover:text-white'
            }`}
          >
            {f === 'all' ? 'سب' : 'نہ پڑھے'}
            {f === 'unread' && unreadCount > 0 && ` (${unreadCount})`}
          </button>
        ))}
      </div>

      {/* Notification list */}
      {filtered.length === 0 ? (
        <div className="glass-card p-10 text-center">
          <HiBell className="text-5xl text-slate-600 mx-auto" />
          <p className="text-slate-400 mt-4 urdu-text">کوئی اطلاع نہیں</p>
          <p className="text-slate-500 text-sm">No notifications</p>
        </div>
      ) : (
        <div className="space-y-2">
          {filtered.map((notif, i) => (
            <div
              key={notif.id}
              onClick={() => markRead(notif.id)}
              className={`glass-card p-4 flex items-start gap-3 cursor-pointer transition-all animate-fade-up ${
                !notif.isRead ? 'border-l-2 border-l-green-500' : 'opacity-70'
              }`}
              style={{ animationDelay: `${i * 0.05}s` }}
            >
              <span className="text-2xl mt-0.5">{notif.icon}</span>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <p className={`font-semibold text-sm urdu-text ${!notif.isRead ? 'text-white' : 'text-slate-400'}`}>
                    {notif.titleUr}
                  </p>
                  {!notif.isRead && (
                    <div className="w-2 h-2 bg-green-500 rounded-full flex-shrink-0 ml-2" />
                  )}
                </div>
                <p className="text-slate-400 text-xs mt-0.5 urdu-text leading-relaxed">
                  {notif.bodyUr}
                </p>
                <p className="text-slate-600 text-xs mt-1">{notif.bodyEn}</p>
                <p className="text-slate-600 text-[10px] mt-2 urdu-text">{notif.time}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
