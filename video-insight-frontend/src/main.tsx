import React from 'react';
import ReactDOM from 'react-dom/client';
import { App as AntApp, ConfigProvider, theme } from 'antd';
import App from './App';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#e0a85e',
          colorSuccess: '#7fc594',
          colorWarning: '#e6b455',
          colorError: '#d96b6b',
          colorBgBase: '#0c0c0d',
          colorTextBase: '#ededec',
          borderRadius: 8,
          fontFamily:
            "'Geist', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
        },
        components: {
          Modal: {
            contentBg: '#141416',
            headerBg: '#141416',
            footerBg: '#141416',
          },
          Message: {
            contentBg: '#141416',
          },
          Progress: {
            defaultColor: '#e0a85e',
          },
        },
      }}
    >
      <AntApp>
        <App />
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
