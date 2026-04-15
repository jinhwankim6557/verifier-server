import {
  Box, Button, FormControlLabel, IconButton, MenuItem, Select, Switch,
  TextField, Typography, styled, InputLabel, FormControl, SelectChangeEvent,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import { useDialogs } from '@toolpad/core';
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { getScopeMapping, putScopeMapping, fetchTasCredentialSchemas } from '../../../apis/oid4vp-api';
import CustomDialog from '../../../components/dialog/CustomDialog';
import SearchDialog from '../../../components/dialog/SearchDialog';
import FullscreenLoader from '../../../components/loading/FullscreenLoader';

interface ClaimEntry {
  id: string;
  path: string;
  values: string;
}

interface CredentialQueryData {
  credentialId: string;
  format: string;
  metaValues: string;
  claims: ClaimEntry[];
}

interface ScopeMappingFormData {
  scope: string;
  description: string;
  enabled: boolean;
}

interface ErrorState {
  scope?: string;
  credentialId?: string;
}

const FORMATS = ['dc+sd-jwt', 'vc+sd-jwt', 'opendid_vc'] as const;

const getMetaKey = (format: string) =>
  format === 'opendid_vc' ? 'credential_schema_id_values' : 'vct_values';

const getMetaHint = (format: string) =>
  format === 'opendid_vc'
    ? 'Credential Schema ID list (use TAS Search)'
    : 'VCT values for dc+sd-jwt / vc+sd-jwt';

const parseDcqlToForm = (dcqlStr: string): CredentialQueryData => {
  const defaultVal: CredentialQueryData = {
    credentialId: '', format: 'dc+sd-jwt', metaValues: '', claims: [],
  };
  try {
    const dcql = typeof dcqlStr === 'string' ? JSON.parse(dcqlStr) : dcqlStr;
    if (!dcql?.credentials?.length) return defaultVal;
    const cred = dcql.credentials[0];
    const format = cred.format || 'dc+sd-jwt';
    const metaKey = getMetaKey(format);
    const metaValues = cred.meta?.[metaKey] ? JSON.stringify(cred.meta[metaKey]) : '';
    const claims: ClaimEntry[] = (cred.claims || []).map((c: any) => ({
      id: c.id || '',
      path: c.path ? JSON.stringify(c.path) : '',
      values: c.values ? JSON.stringify(c.values) : '',
    }));
    return {
      credentialId: cred.id || '',
      format,
      metaValues,
      claims,
    };
  } catch {
    return defaultVal;
  }
};

const ScopeMappingEditPage = () => {
  const { id } = useParams();
  const mappingId = id ? parseInt(id, 10) : null;
  const navigate = useNavigate();
  const dialogs = useDialogs();

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errors, setErrors] = useState<ErrorState>({});
  const [isButtonDisabled, setIsButtonDisabled] = useState(true);

  const [formData, setFormData] = useState<ScopeMappingFormData>({
    scope: '', description: '', enabled: true,
  });
  const [originalFormData, setOriginalFormData] = useState<ScopeMappingFormData | null>(null);

  const [credQuery, setCredQuery] = useState<CredentialQueryData>({
    credentialId: '', format: 'dc+sd-jwt', metaValues: '', claims: [],
  });
  const [originalCredQuery, setOriginalCredQuery] = useState<CredentialQueryData | null>(null);

  const [tasSearchOpen, setTasSearchOpen] = useState(false);
  const [tasList, setTasList] = useState<{ id: string; title: string }[]>([]);
  const [tasLoading, setTasLoading] = useState(false);

  const isOpendidVc = credQuery.format === 'opendid_vc';

  useEffect(() => {
    const fetchData = async () => {
      if (mappingId === null || isNaN(mappingId)) {
        await dialogs.open(CustomDialog, {
          title: 'Error', message: 'Invalid scope mapping ID.', isModal: true,
        }, {
          onClose: async () => navigate('/oid4vp-management/scope-mapping', { replace: true }),
        });
        return;
      }

      try {
        setIsLoading(true);
        const { data } = await getScopeMapping(mappingId);
        const form: ScopeMappingFormData = {
          scope: data.scope || '',
          description: data.description || '',
          enabled: data.enabled ?? true,
        };
        const cred = parseDcqlToForm(data.dcqlQuery);
        setFormData(form);
        setOriginalFormData(form);
        setCredQuery(cred);
        setOriginalCredQuery(cred);
      } catch (err) {
        console.error('Failed to fetch scope mapping:', err);
        await dialogs.open(CustomDialog, {
          title: 'Error', message: `Failed to fetch scope mapping: ${err}`, isModal: true,
        }, {
          onClose: async () => navigate('/oid4vp-management/scope-mapping', { replace: true }),
        });
      } finally {
        setIsLoading(false);
      }
    };
    fetchData();
  }, [mappingId, dialogs, navigate]);

  useEffect(() => {
    if (!originalFormData || !originalCredQuery) return;
    const formChanged = JSON.stringify(formData) !== JSON.stringify(originalFormData);
    const credChanged = JSON.stringify(credQuery) !== JSON.stringify(originalCredQuery);
    setIsButtonDisabled(!formChanged && !credChanged);
  }, [formData, credQuery, originalFormData, originalCredQuery]);

  const buildDcqlJson = () => {
    const cred: any = {};
    if (credQuery.credentialId) cred.id = credQuery.credentialId;
    cred.format = credQuery.format;

    if (credQuery.metaValues.trim()) {
      const metaKey = getMetaKey(credQuery.format);
      try {
        cred.meta = { [metaKey]: JSON.parse(credQuery.metaValues) };
      } catch {
        cred.meta = { [metaKey]: [credQuery.metaValues.trim()] };
      }
    }

    if (!isOpendidVc && credQuery.claims.length > 0) {
      const claims: any[] = [];
      credQuery.claims.forEach(c => {
        if (!c.path.trim()) return;
        const claim: any = {};
        if (c.id) claim.id = c.id;
        try { claim.path = JSON.parse(c.path); } catch { claim.path = [c.path]; }
        if (c.values.trim()) {
          try { claim.values = JSON.parse(c.values); } catch { claim.values = [c.values]; }
        }
        claims.push(claim);
      });
      if (claims.length > 0) cred.claims = claims;
    }

    return { credentials: [cred] };
  };

  const jsonPreview = useMemo(() => {
    try {
      return JSON.stringify(buildDcqlJson(), null, 2);
    } catch {
      return '{}';
    }
  }, [credQuery]);

  const handleFormatChange = (e: SelectChangeEvent<string>) => {
    const format = e.target.value;
    setCredQuery(prev => ({ ...prev, format, metaValues: '', claims: [] }));
  };

  const addClaim = () => {
    setCredQuery(prev => ({
      ...prev,
      claims: [...prev.claims, { id: '', path: '', values: '' }],
    }));
  };

  const updateClaim = (index: number, field: keyof ClaimEntry, value: string) => {
    setCredQuery(prev => ({
      ...prev,
      claims: prev.claims.map((c, i) => i === index ? { ...c, [field]: value } : c),
    }));
  };

  const removeClaim = (index: number) => {
    setCredQuery(prev => ({
      ...prev,
      claims: prev.claims.filter((_, i) => i !== index),
    }));
  };

  const handleTasSearch = async () => {
    setTasSearchOpen(true);
    try {
      setTasLoading(true);
      const response = await fetchTasCredentialSchemas();
      const items = Array.isArray(response?.data) ? response.data : [];
      setTasList(items.map((item: any) => ({
        id: item.schemaId || item.id || '',
        title: item.name || item.schemaId || '',
      })));
    } catch {
      setTasList([
        { id: 'org.opendid.v1.national-id', title: 'National ID' },
        { id: 'org.opendid.v1.driver-license', title: 'Driver License' },
        { id: 'org.opendid.v1.employee-cert', title: 'Employee Certificate' },
      ]);
    } finally {
      setTasLoading(false);
    }
  };

  const handleTasSelect = (selected: any) => {
    const schemaId = selected.id || selected.title;
    setCredQuery(prev => {
      let arr: string[] = [];
      if (prev.metaValues.trim()) {
        try { arr = JSON.parse(prev.metaValues); } catch { arr = []; }
      }
      if (!arr.includes(schemaId)) arr.push(schemaId);
      return { ...prev, metaValues: JSON.stringify(arr) };
    });
  };

  const validate = () => {
    const tempErrors: ErrorState = {};
    if (!formData.scope) tempErrors.scope = 'Scope is required';
    if (!credQuery.credentialId.trim()) {
      tempErrors.credentialId = 'Credential ID is required';
    } else if (!/^[a-zA-Z0-9_-]+$/.test(credQuery.credentialId)) {
      tempErrors.credentialId = 'Only alphanumeric characters, underscores, and hyphens allowed';
    }
    setErrors(tempErrors);
    return Object.keys(tempErrors).length === 0;
  };

  const handleReset = () => {
    if (originalFormData && originalCredQuery) {
      setFormData(originalFormData);
      setCredQuery(originalCredQuery);
      setErrors({});
    }
  };

  const handleSubmit = async () => {
    if (!validate()) {
      await dialogs.open(CustomDialog, {
        title: 'Validation Error',
        message: 'Please fill in all required fields correctly.',
        isModal: true,
      });
      return;
    }

    try {
      setIsLoading(true);
      const dcqlJson = buildDcqlJson();
      await putScopeMapping(mappingId!, {
        scope: formData.scope,
        description: formData.description,
        enabled: formData.enabled,
        dcqlQuery: JSON.stringify(dcqlJson),
      });
      setIsLoading(false);

      await dialogs.open(CustomDialog, {
        title: 'Success',
        message: 'Scope Mapping has been updated successfully.',
        isModal: true,
      }, {
        onClose: async () => navigate(`/oid4vp-management/scope-mapping/${mappingId}`, { replace: true }),
      });
    } catch (err) {
      console.error('Failed to update scope mapping:', err);
      setIsLoading(false);
      await dialogs.open(CustomDialog, {
        title: 'Error',
        message: `Failed to update scope mapping: ${err}`,
        isModal: true,
      });
    }
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
      <Typography variant="h4">OID4VP Management</Typography>

      <SearchDialog
        open={tasSearchOpen}
        onClose={() => setTasSearchOpen(false)}
        onSelect={handleTasSelect}
        title="TAS Credential Schema Search"
        items={tasList}
        loading={tasLoading}
        idField="id"
      />

      {/* Basic Info */}
      <StyledContainer>
        <StyledTitle>Scope Mapping Edit</StyledTitle>

        <SectionLabel>Basic Information</SectionLabel>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <TextField
            fullWidth required
            label="Scope"
            value={formData.scope}
            variant="outlined" size="small"
            onChange={(e) => setFormData(prev => ({ ...prev, scope: e.target.value }))}
            error={!!errors.scope}
            helperText={errors.scope || 'Unique scope identifier'}
          />
          <TextField
            fullWidth
            label="Description"
            value={formData.description}
            variant="outlined" size="small"
            onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
          />
        </Box>
        <FormControlLabel
          control={
            <Switch
              checked={formData.enabled}
              onChange={(e) => setFormData(prev => ({ ...prev, enabled: e.target.checked }))}
            />
          }
          label="Enabled"
          sx={{ mt: 1 }}
        />
      </StyledContainer>

      {/* DCQL Query Builder */}
      <StyledContainer sx={{ mt: 2 }}>
        <StyledTitle sx={{ fontSize: '18px' }}>CredentialQuery</StyledTitle>

        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, mt: 2 }}>
          <TextField
            fullWidth required
            label="Credential ID"
            value={credQuery.credentialId}
            variant="outlined" size="small"
            onChange={(e) => setCredQuery(prev => ({ ...prev, credentialId: e.target.value }))}
            error={!!errors.credentialId}
            helperText={errors.credentialId || 'Unique credential identifier'}
          />
          <FormControl fullWidth size="small">
            <InputLabel>Format</InputLabel>
            <Select value={credQuery.format} label="Format" onChange={handleFormatChange}>
              {FORMATS.map(f => <MenuItem key={f} value={f}>{f}</MenuItem>)}
            </Select>
          </FormControl>
        </Box>

        {/* Meta */}
        <SectionLabel>Meta</SectionLabel>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <TextField
            fullWidth
            label="Meta Key"
            value={getMetaKey(credQuery.format)}
            variant="outlined" size="small"
            InputProps={{ readOnly: true }}
            sx={{ bgcolor: '#fafafa' }}
            helperText={getMetaHint(credQuery.format)}
          />
          <Box sx={{ display: 'flex', gap: 1 }}>
            <TextField
              fullWidth
              label="Values (JSON Array)"
              value={credQuery.metaValues}
              variant="outlined" size="small"
              onChange={(e) => setCredQuery(prev => ({ ...prev, metaValues: e.target.value }))}
              placeholder='["value1", "value2"]'
              helperText="JSON array format"
            />
            {isOpendidVc && (
              <Button variant="contained" size="small" sx={{ minWidth: 110, height: 40 }}
                onClick={handleTasSearch}>
                TAS Search
              </Button>
            )}
          </Box>
        </Box>

        {/* Claims */}
        {!isOpendidVc && (
          <>
            <SectionLabel>Claims (Optional)</SectionLabel>
            <Box sx={{ display: 'grid', gridTemplateColumns: '120px 1fr 1fr 40px', gap: 1, mb: 1 }}>
              <Typography variant="caption" color="text.secondary">ID</Typography>
              <Typography variant="caption" color="text.secondary">Path (JSON Array)</Typography>
              <Typography variant="caption" color="text.secondary">Values (Optional)</Typography>
              <span />
            </Box>
            {credQuery.claims.map((claim, idx) => (
              <Box key={idx} sx={{ display: 'grid', gridTemplateColumns: '120px 1fr 1fr 40px', gap: 1, mb: 1 }}>
                <TextField
                  size="small" placeholder="claim_id"
                  value={claim.id}
                  onChange={(e) => updateClaim(idx, 'id', e.target.value)}
                />
                <TextField
                  size="small" placeholder='["fieldName"]'
                  value={claim.path}
                  onChange={(e) => updateClaim(idx, 'path', e.target.value)}
                />
                <TextField
                  size="small" placeholder='["val1","val2"]'
                  value={claim.values}
                  onChange={(e) => updateClaim(idx, 'values', e.target.value)}
                />
                <IconButton size="small" color="error" onClick={() => removeClaim(idx)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Box>
            ))}
            <Button
              variant="outlined" size="small" startIcon={<AddIcon />}
              onClick={addClaim} sx={{ mt: 1 }}
            >
              Add Claim
            </Button>
          </>
        )}
        {isOpendidVc && (
          <Box sx={{ mt: 2, p: 1.5, bgcolor: '#fff3e0', borderRadius: 1, border: '1px solid #ffe0b2' }}>
            <Typography variant="body2" color="text.secondary">
              opendid_vc format submits the full credential. Individual claims are not specified.
            </Typography>
          </Box>
        )}

        {/* JSON Preview */}
        <SectionLabel>DCQL Query JSON Preview</SectionLabel>
        <TextField
          fullWidth multiline
          minRows={6} maxRows={15}
          value={jsonPreview}
          variant="outlined"
          InputProps={{
            readOnly: true,
            sx: { fontFamily: 'monospace', fontSize: '12px', bgcolor: '#fafafa' },
          }}
        />
      </StyledContainer>

      <Box sx={{ display: 'flex', justifyContent: 'center', gap: 2, mt: 3, mb: 4 }}>
        <Button variant="contained" color="primary" onClick={handleSubmit} disabled={isButtonDisabled}>
          Save
        </Button>
        <Button variant="contained" color="secondary" onClick={handleReset}>
          Reset
        </Button>
        <Button variant="outlined" color="secondary"
          onClick={() => navigate(`/oid4vp-management/scope-mapping/${mappingId}`)}>
          Cancel
        </Button>
      </Box>
    </>
  );
};

export default ScopeMappingEditPage;
