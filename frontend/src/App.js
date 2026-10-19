import React, { useState, useCallback, useEffect } from 'react';
import axios from 'axios';
import './App.css';

function App() {
  const [files, setFiles] = useState([]);
  const [title, setTitle] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStatus, setUploadStatus] = useState(null);
  const [jobId, setJobId] = useState(null);
  const [jobs, setJobs] = useState([]);
  const [sortByTimestamp, setSortByTimestamp] = useState(false);

  const handleDragEnter = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  }, []);

  const handleDragOver = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    
    const droppedFiles = Array.from(e.dataTransfer.files);
    const videoFiles = droppedFiles.filter(file => 
      file.type.startsWith('video/')
    );
    
    if (videoFiles.length > 0) {
      setFiles(prev => [...prev, ...videoFiles]);
    }
  }, []);

  const handleFileChange = useCallback((e) => {
    const selectedFiles = Array.from(e.target.files);
    const videoFiles = selectedFiles.filter(file => 
      file.type.startsWith('video/')
    );
    
    if (videoFiles.length > 0) {
      setFiles(prev => [...prev, ...videoFiles]);
    }
  }, []);

  const removeFile = useCallback((index) => {
    setFiles(prev => prev.filter((_, i) => i !== index));
  }, []);

  const handleSubmit = useCallback(async (e) => {
    e.preventDefault();
    
    if (files.length === 0) {
      setUploadStatus({ type: 'error', message: 'Please add at least one video file' });
      return;
    }

    setIsUploading(true);
    setUploadProgress(0);
    setUploadStatus(null);

    try {
      const formData = new FormData();
      
      files.forEach((file, index) => {
        formData.append('files', file);
      });
      
      if (title) {
        formData.append('title', title);
      }

      const response = await axios.post('/api/video/process', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        },
        onUploadProgress: (progressEvent) => {
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total
          );
          setUploadProgress(percentCompleted);
        }
      });

      if (response.data && response.data.id) {
        const backendJobId = response.data.id;
        setJobId(backendJobId);
        const fileNames = files.map(f => f.name);
        addJobToTracker(backendJobId, fileNames);
        setUploadStatus({ 
          type: 'success', 
          message: `Upload successful! Job ID: ${backendJobId}` 
        });
        setFiles([]);
        setTitle('');
      } else {
        setUploadStatus({ 
          type: 'success', 
          message: 'Upload successful! Processing started.' 
        });
        setFiles([]);
        setTitle('');
      }
    } catch (error) {
      console.error('Upload error:', error);
      const errorMessage = error.response?.data?.message || 
                           error.message || 
                           'Failed to upload files';
      setUploadStatus({ type: 'error', message: errorMessage });
    } finally {
      setIsUploading(false);
      setUploadProgress(0);
    }
  }, [files, title]);

  const formatFileSize = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const extractTimestampFromFilename = (filename) => {
    if (!filename) return null;
    
    let match;
    
    const yyyyMmDdPattern = /\b(\d{4})(\d{2})(\d{2})\b/;
    match = filename.match(yyyyMmDdPattern);
    if (match) {
      try {
        const year = parseInt(match[1], 10);
        const month = parseInt(match[2], 10) - 1;
        const day = parseInt(match[3], 10);
        const date = new Date(Date.UTC(year, month, day));
        if (!isNaN(date.getTime())) {
          return date.getTime();
        }
      } catch (e) {
        // Invalid date, try next pattern
      }
    }
    
    const yyyyMmDdSeparatedPattern = /\b(\d{4})[-/](\d{2})[-/](\d{2})\b/;
    match = filename.match(yyyyMmDdSeparatedPattern);
    if (match) {
      try {
        const year = parseInt(match[1], 10);
        const month = parseInt(match[2], 10) - 1;
        const day = parseInt(match[3], 10);
        const date = new Date(Date.UTC(year, month, day));
        if (!isNaN(date.getTime())) {
          return date.getTime();
        }
      } catch (e) {
        // Invalid date
      }
    }
    
    return null;
  };

  const getFileTimestamp = (file) => {
    const filenameTimestamp = extractTimestampFromFilename(file.name);
    if (filenameTimestamp !== null) {
      return filenameTimestamp;
    }
    
    if (file.lastModified) {
      return file.lastModified;
    }
    
    return 0;
  };

  const getSortedFiles = useCallback((filesToSort) => {
    if (!sortByTimestamp) {
      return filesToSort;
    }
    
    return [...filesToSort].sort((a, b) => {
      const timestampA = getFileTimestamp(a);
      const timestampB = getFileTimestamp(b);
      
      if (timestampA === timestampB) {
        return a.name.localeCompare(b.name);
      }
      
      return timestampA - timestampB;
    });
  }, [sortByTimestamp]);

  const fetchJobStatus = useCallback(async (jobId) => {
    try {
      const response = await axios.get(`/api/video/status/${jobId}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching job status:', error);
      if (error.response?.status === 404) {
        return { status: 'UNKNOWN', batchStatus: 'UNKNOWN' };
      }
      return null;
    }
  }, []);

  const addJobToTracker = useCallback((jobId, fileNames) => {
    setJobs(prev => [
      ...prev,
      {
        id: jobId,
        fileNames,
        status: 'PROCESSING',
        progress: 0,
        completedItems: 0,
        totalItems: fileNames.length,
        createdAt: new Date().toISOString()
      }
    ]);
  }, []);

  const removeJobFromTracker = useCallback((jobId) => {
    setJobs(prev => prev.filter(job => job.id !== jobId));
  }, []);

  const updateJobStatus = useCallback((jobId, updates) => {
    setJobs(prev => prev.map(job =>
      job.id === jobId ? { ...job, ...updates } : job
    ));
  }, []);

  const getJobStatusColor = (status) => {
    switch (status) {
      case 'COMPLETED': return '#10b981';
      case 'FAILED': return '#ef4444';
      case 'PROCESSING': return '#3b82f6';
      default: return '#6b7280';
    }
  };

  // Poll job status every 2 seconds
  useEffect(() => {
    const interval = setInterval(() => {
      jobs.forEach(async (job) => {
        if (job.status === 'PROCESSING') {
          const statusData = await fetchJobStatus(job.id);
          if (statusData) {
            const batchStatus = statusData.batchStatus || statusData.status;
            const newStatus = mapBatchStatus(batchStatus);
            const updates = { status: newStatus };
            
            // Update progress counters if available
            if (statusData.completedItems !== undefined && statusData.totalItems !== undefined) {
              updates.completedItems = statusData.completedItems;
              updates.totalItems = statusData.totalItems;
              updates.progress = statusData.totalItems > 0 
                ? Math.round((statusData.completedItems / statusData.totalItems) * 100) 
                : 0;
            }
            
            if (newStatus !== job.status || updates.completedItems !== undefined) {
              updateJobStatus(job.id, updates);
            }
          }
        }
      });
    }, 2000);

    return () => clearInterval(interval);
  }, [jobs, fetchJobStatus, updateJobStatus]);

  const mapBatchStatus = (batchStatus) => {
    if (!batchStatus) return 'PROCESSING';
    const statusUpper = batchStatus.toUpperCase();
    if (statusUpper.includes('COMPLETED') || statusUpper.includes('FINISHED')) return 'COMPLETED';
    if (statusUpper.includes('FAILED') || statusUpper.includes('ERROR')) return 'FAILED';
    if (statusUpper.includes('STARTED') || statusUpper.includes('RUNNING')) return 'PROCESSING';
    return 'PROCESSING';
  };

  const sortedFiles = getSortedFiles(files);

  return (
    <div className="app-container">
      <header className="app-header">
        <h1>Sigil Video Upload</h1>
        <p className="subtitle">Drag and drop videos to process</p>
      </header>

      <main className="upload-container">
        <form onSubmit={handleSubmit} className="upload-form">
          <div 
            className={`drop-zone ${isDragging ? 'dragging' : ''}`}
            onDragEnter={handleDragEnter}
            onDragLeave={handleDragLeave}
            onDragOver={handleDragOver}
            onDrop={handleDrop}
          >
            <input
              type="file"
              id="file-input"
              onChange={handleFileChange}
              multiple
              accept="video/*"
              style={{ display: 'none' }}
            />
            
            <label htmlFor="file-input" className="file-label">
              <div className="drop-icon">
                {files.length === 0 ? (
                  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                    <polyline points="17 8 12 3 7 8"/>
                    <line x1="12" y1="3" x2="12" y2="15"/>
                  </svg>
                ) : (
                  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10"/>
                    <polyline points="12 6 12 12 16 14"/>
                  </svg>
                )}
              </div>
              
              {files.length === 0 ? (
                <>
                  <h3>Drop videos here or click to browse</h3>
                  <p className="file-types">Supports: MP4, WebM, QuickTime, and more</p>
                </>
              ) : (
                <>
                  <h3>{files.length} video{files.length > 1 ? 's' : ''} selected</h3>
                  <p className="file-info">
                    Total size: {formatFileSize(files.reduce((sum, file) => sum + file.size, 0))}
                  </p>
                </>
              )}
            </label>

            {isDragging && (
              <div className="drag-overlay">
                <p>Drop files here</p>
              </div>
            )}
          </div>

          {files.length > 0 && (
            <div className="file-list">
              <h4>Selected Files:
                <label className="sort-checkbox">
                  <input
                    type="checkbox"
                    checked={sortByTimestamp}
                    onChange={(e) => setSortByTimestamp(e.target.checked)}
                  />
                  <span className="checkbox-label">Sort by timestamp</span>
                </label>
              </h4>
              <ul>
                {sortedFiles.map((file, index) => (
                  <li key={index} className="file-item">
                    <span className="file-name">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <polygon points="23 7 16 12 23 17 23 7"/>
                        <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
                      </svg>
                      {file.name}
                    </span>
                    <span className="file-size">{formatFileSize(file.size)}</span>
                    <button 
                      type="button" 
                      onClick={() => removeFile(files.indexOf(file))}
                      className="remove-btn"
                      title="Remove"
                    >
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <circle cx="12" cy="12" r="10"/>
                        <line x1="15" y1="9" x2="9" y2="15"/>
                        <line x1="9" y1="9" x2="15" y2="15"/>
                      </svg>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="form-group">
            <label htmlFor="title">Video Title (Optional)</label>
            <input
              type="text"
              id="title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Enter a title for your video(s)"
              className="title-input"
            />
          </div>

          {uploadStatus && (
            <div className={`status-message ${uploadStatus.type}`}>
              <span className="status-icon">
                {uploadStatus.type === 'success' ? (
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                    <polyline points="22 4 12 14.01 9 11.01"/>
                  </svg>
                ) : (
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="15" y1="9" x2="9" y2="15"/>
                    <line x1="9" y1="9" x2="15" y2="15"/>
                  </svg>
                )}
              </span>
              <span>{uploadStatus.message}</span>
            </div>
          )}

          {isUploading && (
            <div className="progress-container">
              <div className="progress-bar" style={{ width: `${uploadProgress}%` }}></div>
              <span className="progress-text">{uploadProgress}%</span>
            </div>
          )}

          <button 
            type="submit" 
            disabled={files.length === 0 || isUploading}
            className={`submit-btn ${files.length === 0 || isUploading ? 'disabled' : ''}`}
          >
            {isUploading ? (
              <>
                <span className="spinner"></span>
                Processing...
              </>
            ) : (
              'Upload & Process'
            )}
          </button>
        </form>
      </main>

      <footer className="app-footer">
        <p>Sigil Video Processing Portal</p>
      </footer>

      {jobs.length > 0 && (
        <div className="job-tracker">
          {jobs.map((job) => (
            <div key={job.id} className="job-pill" style={{ borderColor: getJobStatusColor(job.status) }}>
              <div className="job-pill-header">
                <span className="job-status-dot" style={{ backgroundColor: getJobStatusColor(job.status) }}></span>
                <span className="job-id">Job: {String(job.id).substring(0, 8)}...</span>
                <button 
                  className="job-close-btn"
                  onClick={() => removeJobFromTracker(job.id)}
                  title="Dismiss"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="15" y1="9" x2="9" y2="15"/>
                    <line x1="9" y1="9" x2="15" y2="15"/>
                  </svg>
                </button>
              </div>
              <div className="job-files">
                {job.fileNames.length > 0 && (
                  <span className="job-file-count">{job.fileNames.length} file{job.fileNames.length > 1 ? 's' : ''}</span>
                )}
                {job.totalItems && job.completedItems !== undefined && (
                  <span className="job-progress">
                    {job.completedItems}/{job.totalItems}
                  </span>
                )}
              </div>
              <div className="job-status">{job.status}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default App;
