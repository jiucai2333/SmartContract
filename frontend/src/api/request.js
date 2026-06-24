export function authHeaders(options = {}) {
  const token = localStorage.getItem('accessToken') || '';
  const isFormData = options.body instanceof FormData;
  return {
    ...(!isFormData ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {})
  };
}

async function readJson(response, fallback) {
  try {
    const data = await response.json();
    return typeof data === 'string' ? JSON.parse(data) : data;
  } catch {
    throw new Error(`${fallback}：${response.status}`);
  }
}

export async function api(url, options = {}) {
  const response = await fetch(url, { ...options, headers: authHeaders(options) });
  const data = await readJson(response, '请求失败');
  if (data && typeof data.code === 'number') {
    if (data.code === 401) window.location.href = '/login';
    if (data.code !== 200) throw new Error(data.msg || '请求失败');
    return data.data;
  }
  if (!response.ok) throw new Error(data.message || data.msg || `请求失败：${response.status}`);
  return data;
}

export async function request(url, options = {}) {
  const response = await fetch(url, { ...options, headers: authHeaders(options) });
  const data = await readJson(response, '请求失败');
  if (data.code !== 200) throw new Error(data.msg || '请求失败');
  return data;
}
