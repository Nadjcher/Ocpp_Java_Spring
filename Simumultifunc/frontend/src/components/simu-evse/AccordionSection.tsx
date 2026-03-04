// src/components/simu-evse/AccordionSection.tsx
// Composant generique de section repliable (accordeon)

import React, { useState } from 'react';

interface AccordionSectionProps {
  title: string;
  icon?: React.ReactNode;
  defaultOpen?: boolean;
  summary?: string;      // Resume visible quand la section est fermee
  badge?: React.ReactNode;
  children: React.ReactNode;
}

export function AccordionSection({
  title,
  icon,
  defaultOpen = false,
  summary,
  badge,
  children,
}: AccordionSectionProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm overflow-hidden">
      {/* Header cliquable */}
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between px-4 py-3 hover:bg-slate-50 transition-colors"
      >
        <div className="flex items-center gap-2">
          {icon && <span className="text-slate-500">{icon}</span>}
          <span className="font-semibold text-sm text-slate-800">{title}</span>
          {badge}
        </div>
        <div className="flex items-center gap-3">
          {/* Resume quand ferme */}
          {!open && summary && (
            <span className="text-xs text-slate-400 max-w-[300px] truncate">{summary}</span>
          )}
          <svg
            className={`w-4 h-4 text-slate-400 transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </button>

      {/* Contenu */}
      <div className={`transition-all duration-200 ${open ? 'max-h-[2000px] opacity-100' : 'max-h-0 opacity-0 overflow-hidden'}`}>
        <div className="px-4 pb-4 pt-1 border-t border-slate-100">
          {children}
        </div>
      </div>
    </div>
  );
}

export default AccordionSection;
