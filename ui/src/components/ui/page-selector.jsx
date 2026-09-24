import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination';

const PageSelector = ({ pagesCount, currentPage, onClick }) => {
  console.log(
    `Rendering PageSelector with pagesCount=${pagesCount}, currentPage=${currentPage}`
  );
  const paginationItems = [];

  // Case 1. CP < 5:
  // 1 2 3 4 5 ... E
  // 1 + 2->5 + ... + E
  // -------------------------------
  // Case 2. 5 <= CP <= E-5:
  // 1 ... CP-1 CP CP+1 ... E
  // 1 + ... + CP-1->CP+1 + ... + E
  // -------------------------------
  // Case 3. CP > E-5
  // 1 ... E-4 E-3 E-2 E-1 E
  // 1 + ... + E-4->E

  // Always add the first page
  paginationItems.push(
    <PaginationItem key={1}>
      <PaginationLink
        className={'cursor-pointer'}
        isActive={currentPage === 1}
        onClick={() => {
          if (1 != currentPage) {
            onClick(1);
          }
        }}
      >
        1
      </PaginationLink>
    </PaginationItem>
  );

  // Add ellipsis after first page if we are beyond page 4
  if (currentPage >= 5) {
    paginationItems.push(<PaginationEllipsis key="ellipsis1" />);
  }

  // Calculate the range of pages to display around the current page
  // Case 1: if before page 5, show pages 2 to 5
  // Case 2: if after E-4, show 4 pages before the end
  // Case 3: otherwise, show one page before and one after the current
  const startPage =
    currentPage < 5
      ? 2
      : currentPage > pagesCount - 4
        ? pagesCount - 4
        : currentPage - 1;

  const endPage =
    currentPage < 5
      ? 5
      : currentPage > pagesCount - 4
        ? pagesCount - 1
        : currentPage + 1;

  // Generate pagination items for the range
  for (let page = startPage; page <= endPage; page++) {
    paginationItems.push(
      <PaginationItem key={page}>
        <PaginationLink
          className={'cursor-pointer'}
          isActive={currentPage === page}
          onClick={() => {
            if (page != currentPage) {
              onClick(page);
            }
          }}
        >
          {page}
        </PaginationLink>
      </PaginationItem>
    );
  }

  // If we are before the last 4 pages, add an ellipsis
  if (currentPage <= pagesCount - 4) {
    paginationItems.push(<PaginationEllipsis key="ellipsis2" />);
  }

  // Always add the last page unless there is only 1 page
  if (pagesCount > 1) {
    paginationItems.push(
      <PaginationItem key={pagesCount}>
        <PaginationLink
          className={'cursor-pointer'}
          isActive={currentPage === pagesCount}
          onClick={() => onClick(pagesCount)}
        >
          {pagesCount}
        </PaginationLink>
      </PaginationItem>
    );
  }

  return (
    <Pagination>
      <PaginationContent>
        <PaginationItem key="previous">
          <PaginationPrevious
            className={'cursor-pointer'}
            disabled={currentPage === 1}
            onClick={() => {
              if (currentPage > 1) {
                onClick(currentPage - 1);
              }
            }}
          />
        </PaginationItem>
        {paginationItems}
        <PaginationItem key="next">
          <PaginationNext
            className={'cursor-pointer'}
            disabled={currentPage === pagesCount}
            onClick={() => {
              if (currentPage < pagesCount) {
                onClick(currentPage + 1);
              }
            }}
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  );
};

export default PageSelector;
