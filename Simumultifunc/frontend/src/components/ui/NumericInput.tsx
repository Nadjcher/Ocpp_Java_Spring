// frontend/src/components/ui/NumericInput.tsx
import React, { useState, useEffect, useCallback } from 'react';

interface NumericInputProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
  className?: string;
  placeholder?: string;
}

/**
 * Numeric input component that allows free editing without cursor jumping issues.
 * Uses local string state during editing and syncs to parent on blur.
 */
export function NumericInput({
  value,
  onChange,
  min,
  max,
  step = 1,
  disabled = false,
  className = '',
  placeholder
}: NumericInputProps) {
  const [localValue, setLocalValue] = useState(String(value));
  const [isFocused, setIsFocused] = useState(false);

  // Sync from parent when not focused
  useEffect(() => {
    if (!isFocused) {
      setLocalValue(String(value));
    }
  }, [value, isFocused]);

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    // Allow empty, minus sign, or valid number patterns
    if (val === '' || val === '-' || /^-?\d*\.?\d*$/.test(val)) {
      setLocalValue(val);
      // Immediately update parent if valid number
      const num = parseFloat(val);
      if (!isNaN(num)) {
        onChange(num);
      }
    }
  }, [onChange]);

  const handleBlur = useCallback(() => {
    setIsFocused(false);
    let num = parseFloat(localValue);

    // Handle invalid or empty values
    if (isNaN(num)) {
      num = min ?? 0;
    }

    // Apply constraints
    if (min !== undefined && num < min) num = min;
    if (max !== undefined && num > max) num = max;

    // Round to step if needed
    if (step !== 1) {
      num = Math.round(num / step) * step;
    }

    setLocalValue(String(num));
    onChange(num);
  }, [localValue, min, max, step, onChange]);

  const handleFocus = useCallback(() => {
    setIsFocused(true);
  }, []);

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLInputElement>) => {
    // Handle arrow keys for increment/decrement
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      const current = parseFloat(localValue) || 0;
      let newVal = current + step;
      if (max !== undefined && newVal > max) newVal = max;
      setLocalValue(String(newVal));
      onChange(newVal);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      const current = parseFloat(localValue) || 0;
      let newVal = current - step;
      if (min !== undefined && newVal < min) newVal = min;
      setLocalValue(String(newVal));
      onChange(newVal);
    } else if (e.key === 'Enter') {
      (e.target as HTMLInputElement).blur();
    }
  }, [localValue, step, min, max, onChange]);

  return (
    <input
      type="text"
      inputMode="decimal"
      value={localValue}
      onChange={handleChange}
      onBlur={handleBlur}
      onFocus={handleFocus}
      onKeyDown={handleKeyDown}
      disabled={disabled}
      className={className}
      placeholder={placeholder}
    />
  );
}

export default NumericInput;
