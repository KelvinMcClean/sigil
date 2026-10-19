// Configuration for API endpoints
// This allows easy switching between development and production

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || '';

export const API_ENDPOINTS = {
  PROCESS_VIDEO: `${API_BASE_URL}/api/video/process`,
  JOB_STATUS: (jobId) => `${API_BASE_URL}/api/video/status/${jobId}`,
};

export default API_BASE_URL;
