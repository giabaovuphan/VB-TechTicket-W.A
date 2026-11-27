const MAX_DIMENSION_PX = 720;
const TARGET_BASE64_LENGTH = 260_000;
const HARD_MAX_BASE64_LENGTH = 295_000;

export function normalizeBase64ImageSrc(base64?: string | null): string | null {
  if (!base64 || !base64.trim()) return null;
  const trimmed = base64.trim();
  if (trimmed.startsWith('data:image/')) return trimmed;
  return `data:image/jpeg;base64,${trimmed}`;
}

export function stripDataUrlPrefix(dataUrl: string): string {
  const idx = dataUrl.indexOf('base64,');
  return idx >= 0 ? dataUrl.slice(idx + 'base64,'.length) : dataUrl;
}

export async function compressImageFileToBase64(file: File): Promise<string> {
  if (!file.type.startsWith('image/')) {
    throw new Error('Vui lòng chọn tệp hình ảnh hợp lệ (JPG, PNG, WEBP).');
  }

  const objectUrl = URL.createObjectURL(file);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve(image);
      image.onerror = () => reject(new Error('Không thể đọc tệp ảnh đã chọn.'));
      image.src = objectUrl;
    });

    const width = img.naturalWidth || img.width;
    const height = img.naturalHeight || img.height;
    if (!width || !height) {
      throw new Error('Định dạng ảnh không hợp lệ.');
    }

    const longest = Math.max(width, height);
    const scale = longest > MAX_DIMENSION_PX ? MAX_DIMENSION_PX / longest : 1;
    const targetW = Math.max(1, Math.round(width * scale));
    const targetH = Math.max(1, Math.round(height * scale));

    const canvas = document.createElement('canvas');
    canvas.width = targetW;
    canvas.height = targetH;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      throw new Error('Trình duyệt không hỗ trợ xử lý ảnh.');
    }
    ctx.drawImage(img, 0, 0, targetW, targetH);

    let quality = 0.72;
    let encoded = '';
    do {
      const dataUrl = canvas.toDataURL('image/jpeg', quality);
      encoded = stripDataUrlPrefix(dataUrl);
      quality -= 0.12;
    } while (encoded.length > TARGET_BASE64_LENGTH && quality >= 0.3);

    if (encoded.length > HARD_MAX_BASE64_LENGTH) {
      throw new Error('Ảnh có dung lượng quá lớn, vui lòng chọn ảnh khác.');
    }

    return encoded;
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}
