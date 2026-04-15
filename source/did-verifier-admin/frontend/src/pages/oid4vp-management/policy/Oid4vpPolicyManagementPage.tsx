import { Box, Link, Typography, styled } from '@mui/material';
import { GridPaginationModel } from "@mui/x-data-grid";
import { useDialogs } from "@toolpad/core";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { deleteOid4vpPolicy, fetchOid4vpPolicies } from '../../../apis/oid4vp-api';
import CustomDataGrid from "../../../components/data-grid/CustomDataGrid";
import CustomConfirmDialog from '../../../components/dialog/CustomConfirmDialog';
import CustomDialog from '../../../components/dialog/CustomDialog';
import FullscreenLoader from "../../../components/loading/FullscreenLoader";

type PolicyRow = {
  id: number;
  policyTitle: string;
  scope: string;
  createdAt: string;
};

const Oid4vpPolicyManagementPage = () => {
  const navigate = useNavigate();
  const dialogs = useDialogs();
  const [loading, setLoading] = useState<boolean>(false);
  const [totalRows, setTotalRows] = useState<number>(0);
  const [selectedRow, setSelectedRow] = useState<string | number | null>(null);
  const [rows, setRows] = useState<PolicyRow[]>([]);

  const [paginationModel, setPaginationModel] = useState<GridPaginationModel>({
    page: 0,
    pageSize: 10,
  });

  const selectedRowData = useMemo(
    () => Array.isArray(rows) ? rows.find(row => row.id === selectedRow) || null : null,
    [rows, selectedRow]
  );

  const handleDelete = async () => {
    const id = selectedRowData?.id as number;
    if (id) {
      const result = await dialogs.open(CustomConfirmDialog, {
        title: 'Confirmation',
        message: 'Are you sure you want to delete this OID4VP Policy?',
        isModal: true,
      });

      if (result) {
        setLoading(true);
        deleteOid4vpPolicy(id)
          .then(() => {
            dialogs.open(CustomDialog, {
              title: 'Notification',
              message: 'OID4VP Policy delete completed.',
              isModal: true,
            }, {
              onClose: async () => {
                setPaginationModel(prev => ({ ...prev }));
              },
            });
          })
          .catch((error) => {
            console.error("Failed to delete OID4VP Policy. ", error);
            navigate('/error', { state: { message: `Failed to delete OID4VP Policy: ${error}` } });
          })
          .finally(() => setLoading(false));
      }
    }
  };

  useEffect(() => {
    setLoading(true);
    fetchOid4vpPolicies(paginationModel.page, paginationModel.pageSize, null, null)
      .then((response) => {
        setRows(response.data.content);
        setTotalRows(response.data.totalElements);
      })
      .catch((error) => {
        console.error("Failed to retrieve OID4VP Policies. ", error);
        navigate('/error', { state: { message: `Failed to retrieve OID4VP Policies: ${error}` } });
      })
      .finally(() => setLoading(false));
  }, [paginationModel]);

  const StyledContainer = useMemo(() => styled(Box)(({ theme }) => ({
    margin: 'auto',
    marginTop: theme.spacing(1),
    padding: theme.spacing(3),
    border: 'none',
    borderRadius: theme.shape.borderRadius,
    backgroundColor: '#ffffff',
    boxShadow: '0px 4px 8px 0px #0000001A',
  })), []);

  const StyledSubTitle = useMemo(() => styled(Typography)({
    textAlign: 'left',
    fontSize: '24px',
    fontWeight: 700,
  }), []);

  return (
    <>
      <FullscreenLoader open={loading} />
      <StyledContainer>
        <StyledSubTitle>OID4VP Policy Management</StyledSubTitle>
        <CustomDataGrid
          rows={rows}
          columns={[
            {
              field: 'policyTitle',
              headerName: "Policy Title",
              width: 180,
              renderCell: (params) => (
                <Link
                  component="button"
                  variant='body2'
                  onClick={() => navigate(`/oid4vp-management/policy/${params.row.id}`)}
                  sx={{ cursor: 'pointer', color: 'primary.main' }}
                >
                  {params.value}
                </Link>),
            },
            {
              field: 'scope',
              headerName: "Scope",
              width: 150,
            },
            {
              field: 'createdAt',
              headerName: "Created At",
              width: 180,
            },
          ]}
          selectedRow={selectedRow}
          setSelectedRow={setSelectedRow}
          onEdit={() => {
            if (selectedRowData) {
              navigate(`/oid4vp-management/policy/policy-edit/${selectedRowData.id}`);
            }
          }}
          onRegister={() => navigate('/oid4vp-management/policy/policy-registration')}
          onDelete={handleDelete}
          additionalButtons={[]}
          paginationMode="server"
          totalRows={totalRows}
          paginationModel={paginationModel}
          setPaginationModel={setPaginationModel}
        />
      </StyledContainer>
    </>
  );
};

export default Oid4vpPolicyManagementPage;
