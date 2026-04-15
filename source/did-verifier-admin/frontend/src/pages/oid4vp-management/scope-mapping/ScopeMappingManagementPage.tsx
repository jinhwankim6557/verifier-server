import { Box, Link, Typography, styled } from '@mui/material';
import { GridPaginationModel } from "@mui/x-data-grid";
import { useDialogs } from "@toolpad/core";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { deleteScopeMapping, fetchScopeMappings } from '../../../apis/oid4vp-api';
import CustomDataGrid from "../../../components/data-grid/CustomDataGrid";
import CustomConfirmDialog from '../../../components/dialog/CustomConfirmDialog';
import CustomDialog from '../../../components/dialog/CustomDialog';
import FullscreenLoader from "../../../components/loading/FullscreenLoader";

type ScopeMappingRow = {
  id: number;
  scope: string;
  description: string;
  enabled: boolean;
  createdAt: string;
};

const ScopeMappingManagementPage = () => {
  const navigate = useNavigate();
  const dialogs = useDialogs();
  const [loading, setLoading] = useState<boolean>(false);
  const [totalRows, setTotalRows] = useState<number>(0);
  const [selectedRow, setSelectedRow] = useState<string | number | null>(null);
  const [rows, setRows] = useState<ScopeMappingRow[]>([]);

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
        message: 'Are you sure you want to delete this Scope Mapping?',
        isModal: true,
      });

      if (result) {
        setLoading(true);
        deleteScopeMapping(id)
          .then(() => {
            dialogs.open(CustomDialog, {
              title: 'Notification',
              message: 'Scope Mapping delete completed.',
              isModal: true,
            }, {
              onClose: async () => {
                setPaginationModel(prev => ({ ...prev }));
              },
            });
          })
          .catch((error) => {
            console.error("Failed to delete Scope Mapping. ", error);
            navigate('/error', { state: { message: `Failed to delete Scope Mapping: ${error}` } });
          })
          .finally(() => setLoading(false));
      }
    }
  };

  useEffect(() => {
    setLoading(true);
    fetchScopeMappings(paginationModel.page, paginationModel.pageSize)
      .then((response) => {
        setRows(response.data.content);
        setTotalRows(response.data.totalElements);
      })
      .catch((error) => {
        console.error("Failed to retrieve Scope Mappings. ", error);
        navigate('/error', { state: { message: `Failed to retrieve Scope Mappings: ${error}` } });
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
        <StyledSubTitle>DCQL Scope Mapping</StyledSubTitle>
        <CustomDataGrid
          rows={rows}
          columns={[
            {
              field: 'scope',
              headerName: "Scope",
              width: 200,
              renderCell: (params) => (
                <Link
                  component="button"
                  variant='body2'
                  onClick={() => navigate(`/oid4vp-management/scope-mapping/${params.row.id}`)}
                  sx={{ cursor: 'pointer', color: 'primary.main' }}
                >
                  {params.value}
                </Link>),
            },
            {
              field: 'description',
              headerName: "Description",
              width: 250,
            },
            {
              field: 'enabled',
              headerName: "Enabled",
              width: 100,
              renderCell: (params) => params.value ? 'Yes' : 'No',
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
              navigate(`/oid4vp-management/scope-mapping/scope-mapping-edit/${selectedRowData.id}`);
            }
          }}
          onRegister={() => navigate('/oid4vp-management/scope-mapping/scope-mapping-registration')}
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

export default ScopeMappingManagementPage;
