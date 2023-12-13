import { useEffect, useState } from 'react';
import PurchasesTable from '../purchases/PurchasesTable';
import Statistics from '../statistics/Statistics';
import BigButton from '../../big-button/BigButton';
import LoadingSpinnner from '../../loading-spinner/LoadingSpinner';

function fetchProductDetails(
  product,
  handleDetails,
  handleLoading,
  handleNewPurchase
) {
  handleLoading(true);
  handleNewPurchase(false);
  console.log('Fetching details for product: ', product);
  if (product) {
    fetch(`http://localhost:8080/${product?.id}/details`)
      .then((data) => data.json())
      .then((productDetails) => {
        console.log('Data retrieved:');
        console.log(productDetails);
        handleDetails(productDetails);
        handleLoading(false);
      })
      .catch((error) => {
        console.log(
          'ERROR! Failed to fetch details for product with id: ',
          product.id,
          'Details: ',
          error
        );
        handleLoading(false);
      });
  } else {
    handleLoading(false);
  }
}

function DetailsModal({ product }) {
  const [productDetails, setProductDetails] = useState();
  const [isLoading, setIsLoading] = useState(true);
  const [showNewPurchase, setShowNewPurchase] = useState(false);

  console.log('Rendering DetailsModal with state props product: ', product);
  console.log('Rendering DetailsModal with state props isLoading: ', isLoading);

  console.log(
    'Rendering DetailsModal with state variable productDetails: ',
    productDetails
  );

  useEffect(() => {
    fetchProductDetails(
      product,
      setProductDetails,
      setIsLoading,
      setShowNewPurchase
    );
  }, [product]);
  return (
    <div
      className='modal fade'
      id='details-modal'
      aria-labelledby='detailsModal'
      aria-hidden='true'
    >
      <div className='modal-dialog modal-lg'>
        <div className='modal-content'>
          <div className='modal-header'>
            <h1 className='modal-title'>{product?.name}</h1>
            <button
              type='button'
              className='btn-close'
              data-bs-dismiss='modal'
              aria-label='Close'
            ></button>
          </div>
          <div className='modal-body'>
            {(isLoading && <LoadingSpinnner />) ||
              (productDetails && (
                <>
                  <Statistics stats={productDetails.stats} />
                  <PurchasesTable
                    purchases={productDetails.purchases || []}
                    showNewPurchase={showNewPurchase}
                  />
                  <div className='button-container'>
                    {(showNewPurchase && (
                      <>
                      <BigButton
                        className='new-purchase-cancel'
                        onClickHandler={() => {
                          setShowNewPurchase(true);
                        }}
                      >
                        Отказ
                      </BigButton>
                        <BigButton
                          className='new-purchase-save primary'
                          onClickHandler={() => {
                            setShowNewPurchase(true);
                          }}
                        >
                          Запази
                        </BigButton>
                      </>
                    )) || (
                      <BigButton
                        className='new-purchase-create'
                        onClickHandler={() => {
                          setShowNewPurchase(true);
                        }}
                      >
                        Регистрирай Покупка
                      </BigButton>
                    )}
                  </div>
                </>
              )) || (
                <div className='modal-body'>Няма детайли за този продукт</div>
              )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default DetailsModal;
