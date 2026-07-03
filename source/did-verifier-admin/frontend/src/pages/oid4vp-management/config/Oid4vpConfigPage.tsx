import {
  Box, Button, FormControlLabel, MenuItem, Select, Switch, TextField, Typography, styled,
  InputLabel, FormControl, SelectChangeEvent,
} from '@mui/material';
import { useDialogs } from '@toolpad/core';
import React, { useEffect, useMemo, useState } from 'react';
import { getOid4vpConfig, putOid4vpConfig } from '../../../apis/oid4vp-api';
import { getVerifierInfo } from '../../../apis/verifier-api';
import CustomDialog from '../../../components/dialog/CustomDialog';
import SearchDialog from '../../../components/dialog/SearchDialog';
import FullscreenLoader from '../../../components/loading/FullscreenLoader';

interface OID4VPConfigData {
  baseUrl: string;
  clientName: string;
  invocationScheme: string;
  clientId: { scheme: string; value: string };
  session: { sessionTtl: number };
  endpoints: { response: string; request: string };
  clientMetadata: { vpFormatsSupported: Record<string, object> };
  crypto: { vpTokenEncryptionKey: string | null };
  verification?: { skipX5cChainValidation?: boolean; enforceClaimConstraints?: boolean };
  encryption?: { alg: string; enc: string };
}

const DEFAULT_CONFIG: OID4VPConfigData = {
  baseUrl: '',
  clientName: '',
  invocationScheme: 'openid4vp://',
  clientId: { scheme: 'decentralized_identifier', value: '' },
  session: { sessionTtl: 300000 },
  endpoints: { response: '/oid4vp/response', request: '/oid4vp/request' },
  clientMetadata: {
    vpFormatsSupported: {
      'dc+sd-jwt': {},
      'opendid_vc': {},
      'mso_mdoc': { alg_values: ['ES256'] },
    },
  },
  crypto: { vpTokenEncryptionKey: null },
  verification: { skipX5cChainValidation: false, enforceClaimConstraints: false },
  encryption: { alg: 'ECDH-ES', enc: 'A256GCM' },
};

const Oid4vpConfigPage = () => {
  const dialogs = useDialogs();
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [config, setConfig] = useState<OID4VPConfigData>(DEFAULT_CONFIG);
  const [originalConfig, setOriginalConfig] = useState<OID4VPConfigData>(DEFAULT_CONFIG);
  const [isButtonDisabled, setIsButtonDisabled] = useState(true);
  const [verifierSearchOpen, setVerifierSearchOpen] = useState(false);
  const [verifierSearchMode, setVerifierSearchMode] = useState<'name' | 'did'>('name');
  const [verifierList, setVerifierList] = useState<{ id: string; title: string; did: string }[]>([]);

  useEffect(() => {
    const fetchConfig = async () => {
      try {
        setIsLoading(true);
        const { data } = await getOid4vpConfig();
        const parsed: OID4VPConfigData = {
          baseUrl: data.baseUrl || '',
          clientName: data.clientName || '',
          invocationScheme: data.invocationScheme || 'openid4vp://',
          clientId: {
            scheme: data.clientId?.scheme || 'decentralized_identifier',
            value: data.clientId?.value || '',
          },
          session: { sessionTtl: data.session?.sessionTtl ?? 300000 },
          endpoints: data.endpoints || { response: '/oid4vp/response', request: '/oid4vp/request' },
          clientMetadata: data.clientMetadata || {
            vpFormatsSupported: {
              'dc+sd-jwt': {},
              'opendid_vc': {},
              'mso_mdoc': { alg_values: ['ES256'] },
            },
          },
          verification: data.verification || { skipX5cChainValidation: false, enforceClaimConstraints: false },
          crypto: data.crypto || { vpTokenEncryptionKey: null },
          encryption: data.encryption || { alg: 'ECDH-ES', enc: 'A256GCM' },
        };
        setConfig(parsed);
        setOriginalConfig(parsed);
      } catch (err) {
        console.error('Failed to fetch OID4VP config:', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchConfig();
  }, []);

  useEffect(() => {
    setIsButtonDisabled(JSON.stringify(config) === JSON.stringify(originalConfig));
  }, [config, originalConfig]);

  const handleFieldChange = (field: string, value: string | number) => {
    setConfig(prev => {
      const updated = { ...prev };
      if (field === 'baseUrl') {
        updated.baseUrl = value as string;
        if (updated.clientId.scheme === 'redirect_uri') {
          updated.clientId = { ...updated.clientId, value: (value as string) + '/oid4vp/response' };
        }
      } else if (field === 'clientName') {
        updated.clientName = value as string;
      } else if (field === 'invocationScheme') {
        updated.invocationScheme = value as string;
      } else if (field === 'sessionTtl') {
        updated.session = { sessionTtl: value as number };
      } else if (field === 'clientIdValue') {
        updated.clientId = { ...updated.clientId, value: value as string };
      }
      return updated;
    });
  };

  const handleSchemeChange = (e: SelectChangeEvent<string>) => {
    const scheme = e.target.value;
    setConfig(prev => {
      const value = scheme === 'redirect_uri'
        ? prev.baseUrl + '/oid4vp/response'
        : '';
      return { ...prev, clientId: { scheme, value } };
    });
  };

  const openVerifierSearch = async (mode: 'name' | 'did') => {
    setVerifierSearchMode(mode);
    try {
      const { data } = await getVerifierInfo();
      if (data) {
        setVerifierList([{
          id: data.id?.toString() || '1',
          title: data.name || '',
          did: data.did || '',
        }]);
      } else {
        setVerifierList([]);
      }
    } catch (err) {
      console.error('Failed to fetch verifier info:', err);
      setVerifierList([]);
    }
    setVerifierSearchOpen(true);
  };

  const handleVerifierSelect = (selected: any) => {
    if (verifierSearchMode === 'name') {
      setConfig(prev => ({ ...prev, clientName: selected.title }));
    } else {
      setConfig(prev => ({
        ...prev,
        clientId: { ...prev.clientId, value: selected.did || selected.title },
      }));
    }
  };

  const clientIdPreview = `${config.clientId.scheme}:${config.clientId.value || '(not set)'}`;

  const buildJsonPreview = () => JSON.stringify(config, null, 2);

  const handleSave = async () => {
    if (!config.baseUrl) {
      await dialogs.open(CustomDialog, { title: 'Validation Error', message: 'Base URL is required.', isModal: true });
      return;
    }
    if (!config.clientName) {
      await dialogs.open(CustomDialog, { title: 'Validation Error', message: 'Client Name is required.', isModal: true });
      return;
    }
    if (!config.clientId.value) {
      await dialogs.open(CustomDialog, { title: 'Validation Error', message: 'Client ID Value is required.', isModal: true });
      return;
    }

    try {
      setIsLoading(true);
      await putOid4vpConfig(config);
      setOriginalConfig(config);
      setIsLoading(false);
      await dialogs.open(CustomDialog, { title: 'Success', message: 'OID4VP configuration has been saved successfully.', isModal: true });
    } catch (err) {
      console.error('Failed to save OID4VP config:', err);
      setIsLoading(false);
      await dialogs.open(CustomDialog, { title: 'Error', message: `Failed to save configuration: ${err}`, isModal: true });
    }
  };

  const handleReset = () => {
    setConfig(originalConfig);
  };

  const StyledContainer = useMemo(() => styled(Box)(({ theme }) => ({
    width: 900,
    margin: 'auto',
    marginTop: theme.spacing(1),
    padding: theme.spacing(3),
    border: 'none',
    borderRadius: theme.shape.borderRadius,
    backgroundColor: '#ffffff',
    boxShadow: '0px 4px 8px 0px #0000001A',
  })), []);

  const StyledTitle = useMemo(() => styled(Typography)({
    textAlign: 'left',
    fontSize: '24px',
    fontWeight: 700,
  }), []);

  const SectionLabel = useMemo(() => styled(Typography)(({ theme }) => ({
    fontSize: '13px',
    fontWeight: 700,
    color: theme.palette.primary.main,
    textTransform: 'uppercase' as const,
    borderLeft: `3px solid ${theme.palette.primary.main}`,
    paddingLeft: theme.spacing(1),
    marginTop: theme.spacing(3),
    marginBottom: theme.spacing(1.5),
  })), []);

  return (
    <>
      <FullscreenLoader open={isLoading} />
      <SearchDialog
        open={verifierSearchOpen}
        onClose={() => setVerifierSearchOpen(false)}
        onSelect={handleVerifierSelect}
        title={verifierSearchMode === 'name' ? 'Verifier Name Search' : 'Verifier DID Search'}
        items={verifierList.map(v => ({
          id: v.id,
          title: verifierSearchMode === 'name' ? v.title : v.did,
          did: v.did,
        }))}
        idField="id"
      />

      <StyledContainer>
        <StyledTitle>OID4VP Configuration</StyledTitle>

        <SectionLabel>Basic Information</SectionLabel>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <TextField
            fullWidth required
            label="Base URL"
            value={config.baseUrl}
            variant="outlined" size="small"
            onChange={(e) => handleFieldChange('baseUrl', e.target.value)}
            helperText="Verifier server address"
          />
          <Box sx={{ display: 'flex', gap: 1 }}>
            <TextField
              fullWidth required
              label="Client Name"
              value={config.clientName}
              variant="outlined" size="small"
              onChange={(e) => handleFieldChange('clientName', e.target.value)}
              helperText="Displayed to Wallet users"
            />
            <Button variant="outlined" size="small" sx={{ minWidth: 100, height: 40 }}
              onClick={() => openVerifierSearch('name')}>
              Verifier
            </Button>
          </Box>
        </Box>

        <SectionLabel>Client ID</SectionLabel>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <FormControl fullWidth size="small">
            <InputLabel>Client ID Scheme</InputLabel>
            <Select
              value={config.clientId.scheme}
              label="Client ID Scheme"
              onChange={handleSchemeChange}
            >
              <MenuItem value="redirect_uri">redirect_uri</MenuItem>
              <MenuItem value="decentralized_identifier">decentralized_identifier</MenuItem>
              <MenuItem value="x509_san_dns">x509_san_dns</MenuItem>
            </Select>
          </FormControl>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <TextField
              fullWidth required
              label="Client ID Value"
              value={config.clientId.value}
              variant="outlined" size="small"
              onChange={(e) => handleFieldChange('clientIdValue', e.target.value)}
              helperText={config.clientId.scheme === 'redirect_uri'
                ? 'Auto-generated from Base URL (editable for proxy)'
                : 'Verifier DID (editable for proxy)'}
            />
            {config.clientId.scheme !== 'redirect_uri' && (
              <Button variant="contained" size="small" sx={{ minWidth: 100, height: 40 }}
                onClick={() => openVerifierSearch('did')}>
                DID Search
              </Button>
            )}
          </Box>
        </Box>
        <Box sx={{ mt: 1, p: 1, bgcolor: '#f0f4ff', border: '1px solid #c5cae9', borderRadius: 1 }}>
          <Typography variant="body2" color="text.secondary" component="span">client_id: </Typography>
          <Typography variant="body2" color="primary" fontWeight={600} component="code">{clientIdPreview}</Typography>
        </Box>

        <SectionLabel>Session</SectionLabel>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <TextField
            fullWidth
            label="Session TTL (ms)"
            type="number"
            value={config.session.sessionTtl}
            variant="outlined" size="small"
            inputProps={{ min: 300000, max: 600000 }}
            onChange={(e) => {
              const v = parseInt(e.target.value) || 0;
              handleFieldChange('sessionTtl', Math.max(300000, Math.min(600000, v)));
            }}
            helperText="Min: 300,000ms (5min) / Max: 600,000ms (10min)"
          />
          <TextField
            fullWidth
            label="Invocation Scheme"
            value={config.invocationScheme}
            variant="outlined" size="small"
            onChange={(e) => handleFieldChange('invocationScheme', e.target.value)}
            helperText="Default: openid4vp://"
          />
        </Box>

        <SectionLabel>Encryption</SectionLabel>
        <TextField
          fullWidth
          label="VP Token Encryption Key"
          value={config.crypto?.vpTokenEncryptionKey || '(Not configured)'}
          variant="outlined" size="small"
          InputProps={{ readOnly: true }}
          sx={{ bgcolor: '#fafafa' }}
        />
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
          VP Token 암호화에 사용되는 대칭키입니다. DB seed 또는 직접 설정으로 관리됩니다.
        </Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, mt: 1 }}>
          <TextField
            fullWidth
            label="JWE Key Agreement (alg)"
            value={config.encryption?.alg || 'ECDH-ES'}
            variant="outlined" size="small"
            InputProps={{ readOnly: true }}
            sx={{ bgcolor: '#fafafa' }}
          />
          <TextField
            fullWidth
            label="JWE Content Encryption (enc)"
            value={config.encryption?.enc || 'A256GCM'}
            variant="outlined" size="small"
            InputProps={{ readOnly: true }}
            sx={{ bgcolor: '#fafafa' }}
          />
        </Box>
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
          OID4VP 응답(direct_post.jwt) JWE 암호화 알고리즘입니다. 전체 트랜잭션에 일괄 적용되며(정책별 on/off 없음),
          현재는 읽기전용입니다 — 값을 바꾸려면 Wallet과 사전 합의가 필요합니다.
        </Typography>

        <SectionLabel>Verification (mdoc)</SectionLabel>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, pl: 1 }}>
          <FormControlLabel
            control={
              <Switch
                checked={config.verification?.skipX5cChainValidation ?? false}
                onChange={(e) =>
                  setConfig(prev => ({
                    ...prev,
                    verification: { ...prev.verification, skipX5cChainValidation: e.target.checked },
                  }))
                }
              />
            }
            label={
              <Box>
                <Typography variant="body2">Skip X.509 Chain Validation</Typography>
                <Typography variant="caption" color="text.secondary">
                  PoC/Dev 환경용. 운영 시 반드시 false로 설정하십시오.
                </Typography>
              </Box>
            }
          />
          <FormControlLabel
            control={
              <Switch
                checked={config.verification?.enforceClaimConstraints ?? false}
                onChange={(e) =>
                  setConfig(prev => ({
                    ...prev,
                    verification: { ...prev.verification, enforceClaimConstraints: e.target.checked },
                  }))
                }
              />
            }
            label={
              <Box>
                <Typography variant="body2">Enforce Claim Constraints</Typography>
                <Typography variant="caption" color="text.secondary">
                  DCQL claim constraint 강제 검증 여부.
                </Typography>
              </Box>
            }
          />
        </Box>

        <Box sx={{ display: 'flex', justifyContent: 'center', gap: 2, mt: 4 }}>
          <Button variant="contained" color="primary" onClick={handleSave} disabled={isButtonDisabled}>
            Save
          </Button>
          <Button variant="contained" color="secondary" onClick={handleReset}>
            Reset
          </Button>
        </Box>

        <SectionLabel>JSON Preview</SectionLabel>
        <TextField
          fullWidth multiline
          minRows={10} maxRows={20}
          value={buildJsonPreview()}
          variant="outlined"
          InputProps={{
            readOnly: true,
            sx: { fontFamily: 'monospace', fontSize: '12px', bgcolor: '#fafafa' },
          }}
        />
      </StyledContainer>
    </>
  );
};

export default Oid4vpConfigPage;
