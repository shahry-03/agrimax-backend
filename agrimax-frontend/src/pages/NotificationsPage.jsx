export default function NotificationsPage() {
  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold">🔔 اطلاعات</h2>
      <div className="glass-card p-6 text-center">
        <span className="text-5xl">📭</span>
        <p className="text-slate-400 mt-3">ابھی کوئی اطلاع نہیں</p>
        <p className="text-slate-500 text-sm">No notifications yet</p>
      </div>
    </div>
  );
}
