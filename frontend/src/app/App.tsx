import React from 'react';

export default function App(): React.JSX.Element {
  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-100 p-6">
      <div className="max-w-md w-full bg-white rounded-lg border border-slate-200 shadow-sm p-6 text-center space-y-4">
        <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-emerald-100 text-emerald-600 mb-2 font-bold text-lg">
          VP
        </div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
          Velan Production
        </h1>
        <p className="text-sm text-slate-600 leading-relaxed">
          Frontend starter foundation initialized with React 19, Vite 6, TypeScript, and Tailwind CSS.
        </p>
        <div className="inline-block px-3 py-1 bg-slate-100 rounded-md text-xs font-medium text-slate-700 border border-slate-200">
          Phase 4.3 — Starter Foundation Active
        </div>
      </div>
    </div>
  );
}
