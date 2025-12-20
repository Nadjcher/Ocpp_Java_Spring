// frontend/src/components/OCPPMessagePanel.tsx
import React, { useState } from 'react';

interface OCPPMessage {
  id: string;
  direction: 'SENT' | 'RECEIVED';
  action: string;
  payload: unknown;
  timestamp: string;
  sessionId?: string;
}

interface OCPPMessagePanelProps {
  messages?: OCPPMessage[];
  maxMessages?: number;
  sessionId?: string;
}

export function OCPPMessagePanel({
  messages = [],
  maxMessages = 100,
  sessionId
}: OCPPMessagePanelProps) {
  const [filter, setFilter] = useState('');
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  const filteredMessages = messages
    .filter(m => !sessionId || m.sessionId === sessionId)
    .filter(m => !filter || m.action.toLowerCase().includes(filter.toLowerCase()))
    .slice(-maxMessages);

  const toggleExpand = (id: string) => {
    setExpandedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  return (
    <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
      <div className="p-3 border-b border-gray-200 bg-gray-50">
        <div className="flex items-center gap-2">
          <input
            type="text"
            placeholder="Filter by action..."
            value={filter}
            onChange={e => setFilter(e.target.value)}
            className="flex-1 px-3 py-1.5 text-sm border border-gray-300 rounded"
          />
          <span className="text-xs text-gray-500">
            {filteredMessages.length} messages
          </span>
        </div>
      </div>

      <div className="max-h-96 overflow-y-auto">
        {filteredMessages.length === 0 ? (
          <div className="p-4 text-center text-gray-500 text-sm">
            No OCPP messages yet
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {filteredMessages.map((msg, idx) => (
              <div
                key={msg.id || idx}
                className={`p-2 text-xs cursor-pointer hover:bg-gray-50 ${
                  msg.direction === 'SENT' ? 'bg-blue-50/30' : 'bg-green-50/30'
                }`}
                onClick={() => toggleExpand(msg.id || String(idx))}
              >
                <div className="flex items-center gap-2">
                  <span className={`font-mono px-1.5 py-0.5 rounded text-[10px] ${
                    msg.direction === 'SENT'
                      ? 'bg-blue-100 text-blue-700'
                      : 'bg-green-100 text-green-700'
                  }`}>
                    {msg.direction === 'SENT' ? 'TX' : 'RX'}
                  </span>
                  <span className="font-medium text-gray-800">{msg.action}</span>
                  <span className="text-gray-400 ml-auto">
                    {new Date(msg.timestamp).toLocaleTimeString()}
                  </span>
                </div>
                {expandedIds.has(msg.id || String(idx)) && (
                  <pre className="mt-2 p-2 bg-gray-800 text-gray-100 rounded text-[10px] overflow-x-auto">
                    {JSON.stringify(msg.payload, null, 2)}
                  </pre>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default OCPPMessagePanel;
