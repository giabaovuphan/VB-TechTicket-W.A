import React, { useId, useRef, useState } from 'react';
import { Camera, ImagePlus, Loader2, Maximize2, Trash2, X } from 'lucide-react';
import { compressImageFileToBase64, normalizeBase64ImageSrc } from '../utils/photoUtils';

export const Base64PhotoPreview: React.FC<{
  base64?: string | null;
  label: string;
  emptyText?: string;
}> = ({ base64, label, emptyText = 'Chưa có hình ảnh' }) => {
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const src = normalizeBase64ImageSrc(base64);

  if (!src) {
    return (
      <div className="flex h-44 flex-col items-center justify-center rounded-xl border border-dashed border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-950/50 p-4 text-center">
        <p className="text-xs font-semibold uppercase tracking-wide text-zinc-600 dark:text-zinc-400">
          {label}
        </p>
        <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">{emptyText}</p>
      </div>
    );
  }

  return (
    <>
      <div className="overflow-hidden rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900">
        <div className="flex items-center justify-between border-b border-zinc-200 dark:border-zinc-800 px-3 py-2 bg-zinc-50/70 dark:bg-zinc-950/40">
          <span className="text-xs font-semibold text-zinc-700 dark:text-zinc-300">{label}</span>
          <button
            type="button"
            onClick={() => setLightboxOpen(true)}
            className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-orange-600 dark:text-orange-400 transition hover:bg-orange-50 dark:hover:bg-orange-950/40 cursor-pointer"
          >
            <Maximize2 className="h-3.5 w-3.5" />
            Phóng to
          </button>
        </div>
        <button
          type="button"
          onClick={() => setLightboxOpen(true)}
          className="block w-full cursor-zoom-in overflow-hidden bg-zinc-100 dark:bg-zinc-950"
        >
          <img
            src={src}
            alt={label}
            className="h-48 w-full object-cover transition duration-200 hover:scale-[1.02]"
          />
        </button>
      </div>

      {lightboxOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={label}
          className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/80 p-4 backdrop-blur-[2px]"
          onClick={() => setLightboxOpen(false)}
        >
          <div
            className="relative max-h-[90vh] max-w-4xl w-full overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-950 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="bg-zinc-950 px-5 py-3.5 flex items-center justify-between border-b border-zinc-800">
              <span className="text-sm font-bold text-white">{label}</span>
              <button
                type="button"
                onClick={() => setLightboxOpen(false)}
                aria-label="Đóng ảnh"
                className="rounded-lg p-1.5 text-zinc-400 transition hover:bg-zinc-800 hover:text-white cursor-pointer"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="max-h-[80vh] overflow-auto bg-zinc-950 p-3">
              <img src={src} alt={label} className="mx-auto max-h-[75vh] w-auto object-contain rounded-lg" />
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export const PhotoCapturePickerCard: React.FC<{
  label: string;
  helperText?: string;
  required?: boolean;
  valueBase64: string | null;
  onChange: (nextBase64: string | null) => void;
  onError?: (msg: string) => void;
}> = ({ label, helperText, required = false, valueBase64, onChange, onError }) => {
  const cameraInputId = useId();
  const galleryInputId = useId();
  const cameraRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [compressing, setCompressing] = useState(false);
  const previewSrc = normalizeBase64ImageSrc(valueBase64);

  const handleFileSelected = async (file?: File | null) => {
    if (!file) return;
    setCompressing(true);
    try {
      const encoded = await compressImageFileToBase64(file);
      onChange(encoded);
    } catch (error) {
      const msg = error instanceof Error ? error.message : 'Không thể xử lý hình ảnh.';
      onError?.(msg);
    } finally {
      setCompressing(false);
      if (cameraRef.current) cameraRef.current.value = '';
      if (galleryRef.current) galleryRef.current.value = '';
    }
  };

  return (
    <div className="rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950/60 p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-xs font-semibold text-zinc-900 dark:text-zinc-100">
            {label}{' '}
            {required && <span className="text-red-600 dark:text-red-400">*</span>}
          </p>
          {helperText && (
            <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">{helperText}</p>
          )}
        </div>
        {valueBase64 && (
          <button
            type="button"
            onClick={() => onChange(null)}
            className="inline-flex items-center gap-1 rounded-lg border border-red-200 dark:border-red-500/40 bg-red-50 dark:bg-red-950/40 px-2.5 py-1.5 text-xs font-semibold text-red-700 dark:text-red-300 transition hover:bg-red-100 dark:hover:bg-red-950/70 cursor-pointer"
          >
            <Trash2 className="h-3.5 w-3.5" />
            Xóa ảnh
          </button>
        )}
      </div>

      {previewSrc ? (
        <div className="mt-3 overflow-hidden rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-100 dark:bg-zinc-900">
          <img src={previewSrc} alt={label} className="max-h-56 w-full object-contain" />
        </div>
      ) : null}

      <div className="mt-3 flex flex-wrap gap-2">
        <input
          ref={cameraRef}
          id={cameraInputId}
          type="file"
          accept="image/*"
          capture="environment"
          className="hidden"
          onChange={(e) => void handleFileSelected(e.target.files?.[0])}
        />
        <input
          ref={galleryRef}
          id={galleryInputId}
          type="file"
          accept="image/*"
          className="hidden"
          onChange={(e) => void handleFileSelected(e.target.files?.[0])}
        />

        <label
          htmlFor={cameraInputId}
          className="inline-flex min-h-[42px] flex-1 cursor-pointer items-center justify-center gap-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 px-3 py-2 text-xs font-semibold text-zinc-800 dark:text-zinc-100 transition hover:border-orange-600 hover:text-orange-600 dark:hover:text-orange-400"
        >
          {compressing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Camera className="h-4 w-4" />}
          <span>Chụp ảnh</span>
        </label>

        <label
          htmlFor={galleryInputId}
          className="inline-flex min-h-[42px] flex-1 cursor-pointer items-center justify-center gap-2 rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900 px-3 py-2 text-xs font-semibold text-zinc-800 dark:text-zinc-100 transition hover:border-orange-600 hover:text-orange-600 dark:hover:text-orange-400"
        >
          {compressing ? <Loader2 className="h-4 w-4 animate-spin" /> : <ImagePlus className="h-4 w-4" />}
          <span>Chọn từ thư viện</span>
        </label>
      </div>
    </div>
  );
};
